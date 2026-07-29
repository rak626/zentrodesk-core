package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.common.observability.SensitiveValueSanitizer;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageType;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversation;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationRepository;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationStatus;
import com.rakesh.zentrodesk.sales.customer.ContactNormalizer;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WhatsAppWebhookInboxProcessor {

    private final WhatsAppWebhookInboxRepository webhookRepository;
    private final WhatsAppConfigurationRepository configurationRepository;
    private final CustomerConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationMetrics metrics;

    @Value("${app.whatsapp.webhook-max-attempts:8}")
    private int maxAttempts;

    @Value("${app.whatsapp.webhook-processing-enabled:true}")
    private boolean processingEnabled;

    @Scheduled(fixedDelayString = "${app.whatsapp.webhook-processing-interval:1000}")
    public void processPending() {
        if (!processingEnabled) {
            return;
        }
        webhookRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        WhatsAppWebhookStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(WhatsAppWebhookInbox::getId)
                .forEach(this::processOne);
    }

    public void processPendingNow() {
        webhookRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        WhatsAppWebhookStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(WhatsAppWebhookInbox::getId)
                .forEach(this::processOne);
    }

    private void processOne(UUID id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> webhookRepository.findLockedById(id)
                .filter(webhook -> webhook.getStatus() == WhatsAppWebhookStatus.PENDING)
                .filter(webhook -> !webhook.getNextAttemptAt().isAfter(Instant.now()))
                .ifPresent(this::attemptProcessing));
    }

    private void attemptProcessing(WhatsAppWebhookInbox webhook) {
        try {
            JsonNode root = objectMapper.readTree(webhook.getPayload());
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    processValue(webhook, change.path("value"));
                }
            }
            webhook.setStatus(WhatsAppWebhookStatus.PROCESSED);
            webhook.setProcessedAt(Instant.now());
            webhook.setLastError(null);
            metrics.whatsAppWebhook("PROCESSED");
        } catch (RuntimeException exception) {
            failOrRetry(webhook, exception);
        }
    }

    private void processValue(WhatsAppWebhookInbox webhook, JsonNode value) {
        String phoneNumberId = value.path("metadata").path("phone_number_id").asText();
        WhatsAppConfiguration configuration = configurationRepository.findByPhoneNumberId(phoneNumberId)
                .orElseThrow(() -> new WhatsAppDeliveryException(
                        "No tenant configuration matches the webhook phone number",
                        true
                ));
        webhook.setTenantId(configuration.getTenantId());
        webhook.setPhoneNumberId(phoneNumberId);

        String contactName = value.path("contacts").path(0).path("profile").path("name").asText(null);
        for (JsonNode message : value.path("messages")) {
            processInbound(configuration.getTenantId(), phoneNumberId, contactName, message);
        }
        for (JsonNode status : value.path("statuses")) {
            processStatus(configuration.getTenantId(), status);
        }
    }

    private void processInbound(UUID tenantId, String phoneNumberId, String contactName, JsonNode payload) {
        String providerMessageId = payload.path("id").asText();
        if (providerMessageId.isBlank()
                || messageRepository.findByTenantIdAndProviderMessageId(tenantId, providerMessageId).isPresent()) {
            return;
        }
        String phone = payload.path("from").asText();
        String normalizedPhone = ContactNormalizer.phone(phone);
        if (normalizedPhone == null) {
            throw new WhatsAppDeliveryException("Incoming message has no valid sender identity", false);
        }
        Customer customer = customerRepository.findByTenantIdAndNormalizedPhoneAndActiveTrue(
                tenantId,
                normalizedPhone
        ).orElse(null);
        CustomerConversation conversation = conversationRepository.findByTenantIdAndNormalizedPhone(
                        tenantId,
                        normalizedPhone
                )
                .orElseGet(() -> conversationRepository.save(CustomerConversation.builder()
                        .tenantId(tenantId)
                        .customerId(customer == null ? null : customer.getId())
                        .phone(phone)
                        .normalizedPhone(normalizedPhone)
                        .displayName(customer == null ? contactName : customer.getName())
                        .status(CustomerConversationStatus.OPEN)
                        .active(true)
                        .build()));

        Instant receivedAt = providerTimestamp(payload);
        ConversationMessageType messageType = messageType(payload.path("type").asText());
        messageRepository.save(ConversationMessage.builder()
                .tenantId(tenantId)
                .conversationId(conversation.getId())
                .customerId(conversation.getCustomerId())
                .businessPhoneNumberId(phoneNumberId)
                .phone(phone)
                .direction(ConversationMessageDirection.INBOUND)
                .message(messageText(payload, messageType))
                .messageType(messageType)
                .providerMessageId(providerMessageId)
                .status(ConversationMessageStatus.DELIVERED)
                .active(true)
                .build());
        conversation.setStatus(CustomerConversationStatus.OPEN);
        conversation.setLastInboundAt(receivedAt);
        conversation.setLastMessageAt(receivedAt);
        conversation.setUnreadCount(conversation.getUnreadCount() + 1);
    }

    private void processStatus(UUID tenantId, JsonNode payload) {
        String providerMessageId = payload.path("id").asText();
        ConversationMessageStatus nextStatus = messageStatus(payload.path("status").asText());
        if (providerMessageId.isBlank() || nextStatus == null) {
            return;
        }
        messageRepository.findByTenantIdAndProviderMessageId(tenantId, providerMessageId)
                .ifPresent(message -> {
                    if (nextStatus == ConversationMessageStatus.FAILED
                            || statusRank(nextStatus) > statusRank(message.getStatus())) {
                        message.setStatus(nextStatus);
                    }
                });
    }

    private Instant providerTimestamp(JsonNode payload) {
        long epochSeconds = payload.path("timestamp").asLong(0);
        return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
    }

    private ConversationMessageType messageType(String value) {
        if (value == null) {
            return ConversationMessageType.UNKNOWN;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "text" -> ConversationMessageType.TEXT;
            case "image" -> ConversationMessageType.IMAGE;
            case "document" -> ConversationMessageType.DOCUMENT;
            case "template" -> ConversationMessageType.TEMPLATE;
            default -> ConversationMessageType.UNKNOWN;
        };
    }

    private String messageText(JsonNode payload, ConversationMessageType type) {
        String value = switch (type) {
            case TEXT -> payload.path("text").path("body").asText();
            case IMAGE -> payload.path("image").path("caption").asText();
            case DOCUMENT -> payload.path("document").path("filename").asText();
            case TEMPLATE -> payload.path("button").path("text").asText();
            case UNKNOWN -> "";
        };
        return value.isBlank() ? "[" + type.name().toLowerCase(Locale.ROOT) + " message]" : value;
    }

    private ConversationMessageStatus messageStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "sent" -> ConversationMessageStatus.SENT;
            case "delivered" -> ConversationMessageStatus.DELIVERED;
            case "read" -> ConversationMessageStatus.READ;
            case "failed", "deleted" -> ConversationMessageStatus.FAILED;
            default -> null;
        };
    }

    private int statusRank(ConversationMessageStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case SENT -> 1;
            case DELIVERED -> 2;
            case READ -> 3;
            case FAILED -> 4;
        };
    }

    private void failOrRetry(WhatsAppWebhookInbox webhook, RuntimeException exception) {
        int attempts = webhook.getAttempts() + 1;
        boolean retryable = !(exception instanceof WhatsAppDeliveryException deliveryException)
                || deliveryException.isRetryable();
        webhook.setAttempts(attempts);
        webhook.setLastError(SensitiveValueSanitizer.sanitize(
                SensitiveValueSanitizer.safeErrorType(exception) + ": " + exception.getMessage()
        ));
        if (!retryable || attempts >= maxAttempts) {
            webhook.setStatus(WhatsAppWebhookStatus.FAILED);
            metrics.whatsAppWebhook("FAILED");
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        webhook.setNextAttemptAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
        metrics.whatsAppWebhook("RETRY");
    }
}
