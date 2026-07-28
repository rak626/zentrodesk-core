package com.rakesh.zentrodesk.platform.email;

import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.common.observability.SensitiveValueSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailOutboxProcessor {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailTokenCipher emailTokenCipher;
    private final EmailDeliveryGateway emailDeliveryGateway;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationMetrics metrics;

    @Value("${app.email.base-url}")
    private String baseUrl;

    @Value("${app.email.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.email.dispatch-enabled:true}")
    private boolean dispatchEnabled;

    @Scheduled(fixedDelayString = "${app.email.dispatch-interval:5000}")
    public void deliverPending() {
        if (!dispatchEnabled) {
            return;
        }
        emailOutboxRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        EmailOutboxStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(EmailOutboxMessage::getId)
                .forEach(this::deliverOne);
    }

    public void deliverPendingNow() {
        emailOutboxRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        EmailOutboxStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(EmailOutboxMessage::getId)
                .forEach(this::deliverOne);
    }

    private void deliverOne(UUID id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> emailOutboxRepository.findLockedById(id)
                .filter(message -> message.getStatus() == EmailOutboxStatus.PENDING)
                .filter(message -> !message.getNextAttemptAt().isAfter(Instant.now()))
                .ifPresent(this::attemptDelivery));
    }

    private void attemptDelivery(EmailOutboxMessage outbox) {
        try {
            String token = emailTokenCipher.decrypt(outbox.getTokenCiphertext());
            emailDeliveryGateway.deliver(new EmailDeliveryMessage(
                    outbox.getRecipient(),
                    subject(outbox.getTemplate()),
                    actionUrl(outbox.getTemplate(), token)
            ));
            outbox.setStatus(EmailOutboxStatus.SENT);
            outbox.setDeliveredAt(Instant.now());
            outbox.setLastError(null);
            metrics.emailDelivery("SENT");
        } catch (RuntimeException exception) {
            int attempts = outbox.getAttempts() + 1;
            outbox.setAttempts(attempts);
            outbox.setLastError(sanitizedError(exception));
            if (attempts >= maxAttempts) {
                outbox.setStatus(EmailOutboxStatus.FAILED);
                metrics.emailDelivery("FAILED");
            } else {
                outbox.setNextAttemptAt(Instant.now().plus(1L << Math.min(attempts, 10), ChronoUnit.MINUTES));
                metrics.emailDelivery("RETRY");
            }
        }
    }

    private String subject(EmailTemplate template) {
        return template == EmailTemplate.TENANT_INVITATION
                ? "Set up your ZentroDesk account"
                : "Reset your ZentroDesk password";
    }

    private String actionUrl(EmailTemplate template, String token) {
        String path = template == EmailTemplate.TENANT_INVITATION ? "/accept-invitation" : "/reset-password";
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }

    private String sanitizedError(RuntimeException exception) {
        return SensitiveValueSanitizer.sanitize(
                SensitiveValueSanitizer.safeErrorType(exception) + ": " + exception.getMessage()
        );
    }
}
