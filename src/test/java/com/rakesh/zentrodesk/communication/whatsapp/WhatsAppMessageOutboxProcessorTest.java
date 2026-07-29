package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
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
}
