package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageType;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversation;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationRepository;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationStatus;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocument;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentReadyEvent;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentService;
import com.rakesh.zentrodesk.sales.customer.ContactNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class QuotationDocumentWhatsAppListener {

    private final QuotationDocumentService documentService;
    private final WhatsAppConfigurationRepository configurationRepository;
    private final CustomerConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final WhatsAppOutboxAccess outboxAccess;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueue(QuotationDocumentReadyEvent event) {
        if (messageRepository.existsByDocumentId(event.documentId())) {
            return;
        }
        QuotationDocument document = documentService.requireForDelivery(event.documentId());
        WhatsAppConfiguration configuration = configurationRepository.findByTenantId(document.getTenantId())
                .filter(item -> item.getStatus() == WhatsAppConnectionStatus.CONNECTED)
                .filter(WhatsAppConfiguration::isTokenHealthy)
                .filter(item -> item.getFundingStatus() == WhatsAppFundingStatus.FUNDED)
                .filter(item -> item.getAccessTokenExpiresAt() == null
                        || item.getAccessTokenExpiresAt().isAfter(Instant.now()))
                .orElse(null);
        if (configuration == null) {
            documentService.markDeliveryFailed(document.getId(), "Tenant WhatsApp number is not connected");
            return;
        }
        String normalizedPhone = ContactNormalizer.phone(document.getRecipientPhone());
        if (normalizedPhone == null) {
            documentService.markDeliveryFailed(document.getId(), "Quotation recipient phone number is invalid");
            return;
        }
        CustomerConversation conversation = conversationRepository
                .findByTenantIdAndNormalizedPhone(document.getTenantId(), normalizedPhone)
                .orElseGet(() -> conversationRepository.save(CustomerConversation.builder()
                        .tenantId(document.getTenantId())
                        .customerId(document.getCustomerId())
                        .phone(document.getRecipientPhone())
                        .normalizedPhone(normalizedPhone)
                        .displayName(document.getRecipientName())
                        .status(CustomerConversationStatus.OPEN)
                        .active(true)
                        .build()));
        conversation.setQuotationId(document.getQuotationId());
        conversation.setDisplayName(document.getRecipientName());
        conversation.setStatus(CustomerConversationStatus.OPEN);
        conversation.setLastMessageAt(Instant.now());
        ConversationMessage message = messageRepository.save(ConversationMessage.builder()
                .tenantId(document.getTenantId())
                .conversationId(conversation.getId())
                .customerId(document.getCustomerId())
                .businessPhoneNumberId(configuration.getPhoneNumberId())
                .phone(document.getRecipientPhone())
                .direction(ConversationMessageDirection.OUTBOUND)
                .message("Quotation " + document.getFileName())
                .messageType(ConversationMessageType.DOCUMENT)
                .documentId(document.getId())
                .documentFileName(document.getFileName())
                .templateName(document.getWhatsAppTemplateName())
                .templateLanguage(document.getWhatsAppTemplateLanguage())
                .status(ConversationMessageStatus.PENDING)
                .active(true)
                .build());
        outboxAccess.enqueue(WhatsAppMessageOutbox.builder()
                .tenantId(document.getTenantId())
                .conversationId(conversation.getId())
                .messageId(message.getId())
                .senderPhoneNumberId(configuration.getPhoneNumberId())
                .nextAttemptAt(Instant.now())
                .build());
    }
}
