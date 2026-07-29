package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WhatsAppOutboxAccess {

    private final WhatsAppMessageOutboxRepository repository;
    private final ConversationMessageRepository messageRepository;

    public void enqueue(WhatsAppMessageOutbox outbox) {
        repository.save(outbox);
    }

    public void failPending(UUID tenantId, String senderPhoneNumberId, String reason) {
        repository.findAllByTenantIdAndSenderPhoneNumberIdAndStatus(
                tenantId,
                senderPhoneNumberId,
                WhatsAppOutboxStatus.PENDING
        ).forEach(outbox -> {
            outbox.setStatus(WhatsAppOutboxStatus.FAILED);
            outbox.setLastError(reason);
            messageRepository.findByIdAndTenantId(outbox.getMessageId(), tenantId)
                    .ifPresent(message -> message.setStatus(ConversationMessageStatus.FAILED));
        });
    }
}
