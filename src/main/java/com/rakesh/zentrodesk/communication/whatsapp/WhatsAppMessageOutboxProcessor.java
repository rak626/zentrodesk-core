package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.common.observability.SensitiveValueSanitizer;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageType;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocument;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WhatsAppMessageOutboxProcessor {

    private final WhatsAppMessageOutboxRepository outboxRepository;
    private final WhatsAppConfigurationRepository configurationRepository;
    private final ConversationMessageRepository messageRepository;
    private final WhatsAppCredentialAccess credentialAccess;
    private final QuotationDocumentService documentService;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationMetrics metrics;

    @Value("${app.whatsapp.max-attempts:8}")
    private int maxAttempts;

    @Value("${app.whatsapp.dispatch-enabled:false}")
    private boolean dispatchEnabled;

    @Scheduled(fixedDelayString = "${app.whatsapp.dispatch-interval:2000}")
    public void deliverPending() {
        if (!dispatchEnabled) {
            return;
        }
        outboxRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        WhatsAppOutboxStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(WhatsAppMessageOutbox::getId)
                .forEach(this::deliverOne);
    }

    public void deliverPendingNow() {
        outboxRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        WhatsAppOutboxStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(WhatsAppMessageOutbox::getId)
                .forEach(this::deliverOne);
    }

    private void deliverOne(UUID id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> outboxRepository.findLockedById(id)
                .filter(outbox -> outbox.getStatus() == WhatsAppOutboxStatus.PENDING)
                .filter(outbox -> !outbox.getNextAttemptAt().isAfter(Instant.now()))
                .ifPresent(this::attemptDelivery));
    }

    private void attemptDelivery(WhatsAppMessageOutbox outbox) {
        try {
            WhatsAppConfiguration configuration = configurationRepository.findByTenantId(outbox.getTenantId())
                    .filter(item -> item.getStatus() == WhatsAppConnectionStatus.CONNECTED)
                    .filter(WhatsAppConfiguration::isTokenHealthy)
                    .filter(item -> item.getFundingStatus() == WhatsAppFundingStatus.FUNDED)
                    .filter(item -> item.getAccessTokenExpiresAt() == null
                            || item.getAccessTokenExpiresAt().isAfter(Instant.now()))
                    .filter(item -> item.getPhoneNumberId().equals(outbox.getSenderPhoneNumberId()))
                    .orElseThrow(() -> new WhatsAppDeliveryException(
                            "Tenant WhatsApp sender is unavailable or has changed",
                            false
                    ));
            ConversationMessage message = messageRepository.findByIdAndTenantId(
                            outbox.getMessageId(),
                            outbox.getTenantId()
                    )
                    .orElseThrow(() -> new WhatsAppDeliveryException("Conversation message is unavailable", false));
            if (!outbox.getSenderPhoneNumberId().equals(message.getBusinessPhoneNumberId())) {
                throw new WhatsAppDeliveryException("Outbound sender snapshot does not match the message", false);
            }
            String accessToken = credentialAccess.decrypt(configuration.getAccessTokenCiphertext());
            WhatsAppDeliveryResult result = message.getMessageType() == ConversationMessageType.DOCUMENT
                    ? sendDocument(outbox, message, accessToken)
                    : credentialAccess.sendText(
                            outbox.getSenderPhoneNumberId(),
                            accessToken,
                            message.getPhone(),
                            message.getMessage(),
                            message.getId()
                    );
            message.setProviderMessageId(result.providerMessageId());
            message.setStatus(ConversationMessageStatus.SENT);
            outbox.setStatus(WhatsAppOutboxStatus.SENT);
            outbox.setDeliveredAt(Instant.now());
            outbox.setLastError(null);
            if (message.getDocumentId() != null) {
                documentService.markDeliverySent(message.getDocumentId(), result.providerMessageId());
            }
            metrics.whatsAppDelivery("SENT");
        } catch (RuntimeException exception) {
            failOrRetry(outbox, exception);
        }
    }

    private WhatsAppDeliveryResult sendDocument(
            WhatsAppMessageOutbox outbox,
            ConversationMessage message,
            String accessToken
    ) {
        QuotationDocument document = documentService.requireForDelivery(message.getDocumentId());
        return credentialAccess.sendDocumentTemplate(
                outbox.getSenderPhoneNumberId(),
                accessToken,
                message.getPhone(),
                document.getContent(),
                message.getDocumentFileName(),
                message.getTemplateName(),
                message.getTemplateLanguage(),
                message.getId()
        );
    }

    private void failOrRetry(WhatsAppMessageOutbox outbox, RuntimeException exception) {
        int attempts = outbox.getAttempts() + 1;
        boolean retryable = !(exception instanceof WhatsAppDeliveryException deliveryException)
                || deliveryException.isRetryable();
        outbox.setAttempts(attempts);
        outbox.setLastError(SensitiveValueSanitizer.sanitize(
                SensitiveValueSanitizer.safeErrorType(exception) + ": " + exception.getMessage()
        ));
        if (!retryable || attempts >= maxAttempts) {
            outbox.setStatus(WhatsAppOutboxStatus.FAILED);
            messageRepository.findByIdAndTenantId(outbox.getMessageId(), outbox.getTenantId())
                    .ifPresent(message -> {
                        message.setStatus(ConversationMessageStatus.FAILED);
                        if (message.getDocumentId() != null) {
                            documentService.markDeliveryFailed(message.getDocumentId(), outbox.getLastError());
                        }
                    });
            metrics.whatsAppDelivery("FAILED");
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        outbox.setNextAttemptAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
        metrics.whatsAppDelivery("RETRY");
    }
}
