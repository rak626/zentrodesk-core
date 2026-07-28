package com.rakesh.zentrodesk.platform.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionalEmailService {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailTokenCipher emailTokenCipher;

    public void enqueue(UUID tenantId, String recipient, EmailTemplate template, UUID referenceId, String rawToken) {
        emailOutboxRepository.save(EmailOutboxMessage.builder()
                .tenantId(tenantId)
                .recipient(recipient)
                .template(template)
                .referenceId(referenceId)
                .tokenCiphertext(emailTokenCipher.encrypt(rawToken))
                .status(EmailOutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(Instant.now())
                .build());
    }
}
