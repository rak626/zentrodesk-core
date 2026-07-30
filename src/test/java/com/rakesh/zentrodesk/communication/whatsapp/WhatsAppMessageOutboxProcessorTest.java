package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentService;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocument;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppMessageOutboxProcessorTest {

    @Test
    void replacedSenderSnapshotCannotDeliver() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        WhatsAppMessageOutboxRepository outboxRepository = mock(WhatsAppMessageOutboxRepository.class);
        WhatsAppConfigurationRepository configurationRepository = mock(WhatsAppConfigurationRepository.class);
        ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
        WhatsAppCredentialAccess credentialAccess = mock(WhatsAppCredentialAccess.class);
        WhatsAppMessageOutboxProcessor processor = new WhatsAppMessageOutboxProcessor(
                outboxRepository,
                configurationRepository,
                messageRepository,
                credentialAccess,
                mock(QuotationDocumentService.class),
                mock(PlatformTransactionManager.class),
                mock(ApplicationMetrics.class)
        );
        ReflectionTestUtils.setField(processor, "maxAttempts", 1);
        WhatsAppMessageOutbox outbox = WhatsAppMessageOutbox.builder()
                .tenantId(tenantId)
                .messageId(messageId)
                .senderPhoneNumberId("old-phone")
                .status(WhatsAppOutboxStatus.PENDING)
                .build();
        ConversationMessage message = ConversationMessage.builder()
                .id(messageId)
                .tenantId(tenantId)
                .businessPhoneNumberId("old-phone")
                .status(ConversationMessageStatus.PENDING)
                .build();
        WhatsAppConfiguration replacement = WhatsAppConfiguration.builder()
                .tenantId(tenantId)
                .phoneNumberId("new-phone")
                .fundingStatus(WhatsAppFundingStatus.FUNDED)
                .tokenHealthy(true)
                .status(WhatsAppConnectionStatus.CONNECTED)
                .build();
        when(configurationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(replacement));
        when(messageRepository.findByIdAndTenantId(messageId, tenantId)).thenReturn(Optional.of(message));

        ReflectionTestUtils.invokeMethod(processor, "attemptDelivery", outbox);

        assertEquals(WhatsAppOutboxStatus.FAILED, outbox.getStatus());
        assertEquals(ConversationMessageStatus.FAILED, message.getStatus());
        verify(credentialAccess, never()).sendText(any(), any(), any(), any(), any());
    }

    @Test
    void documentMessageUploadsPdfAndSendsUtilityTemplate() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        WhatsAppMessageOutboxRepository outboxRepository = mock(WhatsAppMessageOutboxRepository.class);
        WhatsAppConfigurationRepository configurationRepository = mock(WhatsAppConfigurationRepository.class);
        ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
        WhatsAppCredentialAccess credentialAccess = mock(WhatsAppCredentialAccess.class);
        QuotationDocumentService documentService = mock(QuotationDocumentService.class);
        WhatsAppMessageOutboxProcessor processor = new WhatsAppMessageOutboxProcessor(
                outboxRepository,
                configurationRepository,
                messageRepository,
                credentialAccess,
                documentService,
                mock(PlatformTransactionManager.class),
                mock(ApplicationMetrics.class)
        );
        WhatsAppMessageOutbox outbox = WhatsAppMessageOutbox.builder()
                .tenantId(tenantId)
                .messageId(messageId)
                .senderPhoneNumberId("phone-id")
                .status(WhatsAppOutboxStatus.PENDING)
                .build();
        ConversationMessage message = ConversationMessage.builder()
                .id(messageId)
                .tenantId(tenantId)
                .businessPhoneNumberId("phone-id")
                .phone("919000000000")
                .messageType(ConversationMessageType.DOCUMENT)
                .documentId(documentId)
                .documentFileName("quotation.pdf")
                .templateName("quotation_document")
                .templateLanguage("en")
                .status(ConversationMessageStatus.PENDING)
                .build();
        WhatsAppConfiguration configuration = WhatsAppConfiguration.builder()
                .tenantId(tenantId)
                .phoneNumberId("phone-id")
                .accessTokenCiphertext("encrypted")
                .fundingStatus(WhatsAppFundingStatus.FUNDED)
                .tokenHealthy(true)
                .status(WhatsAppConnectionStatus.CONNECTED)
                .build();
        QuotationDocument document = QuotationDocument.builder()
                .content(new byte[] {1, 2, 3})
                .build();
        when(configurationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(configuration));
        when(messageRepository.findByIdAndTenantId(messageId, tenantId)).thenReturn(Optional.of(message));
        when(credentialAccess.decrypt("encrypted")).thenReturn("token");
        when(documentService.requireForDelivery(documentId)).thenReturn(document);
        when(credentialAccess.sendDocumentTemplate(
                "phone-id",
                "token",
                "919000000000",
                document.getContent(),
                "quotation.pdf",
                "quotation_document",
                "en",
                messageId
        )).thenReturn(new WhatsAppDeliveryResult("wamid.123"));

        ReflectionTestUtils.invokeMethod(processor, "attemptDelivery", outbox);

        assertEquals(WhatsAppOutboxStatus.SENT, outbox.getStatus());
        assertEquals(ConversationMessageStatus.SENT, message.getStatus());
        verify(documentService).markDeliverySent(documentId, "wamid.123");
    }
}
