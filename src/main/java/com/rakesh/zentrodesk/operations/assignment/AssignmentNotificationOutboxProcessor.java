package com.rakesh.zentrodesk.operations.assignment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AssignmentNotificationOutboxProcessor {

    private final AssignmentNotificationOutboxRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.assignment-notifications.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.assignment-notifications.dispatch-enabled:true}")
    private boolean dispatchEnabled;

    @Scheduled(fixedDelayString = "${app.assignment-notifications.dispatch-interval:5000}")
    void dispatchPending() {
        if (!dispatchEnabled) {
            return;
        }
        repository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        AssignmentNotificationStatus.PENDING,
                        Instant.now()
                ).stream()
                .map(AssignmentNotificationOutbox::getId)
                .forEach(this::dispatchOne);
    }

    private void dispatchOne(UUID id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.findLockedById(id)
                .filter(message -> message.getStatus() == AssignmentNotificationStatus.PENDING)
                .filter(message -> !message.getNextAttemptAt().isAfter(Instant.now()))
                .ifPresent(this::attemptDispatch));
    }

    private void attemptDispatch(AssignmentNotificationOutbox outbox) {
        try {
            DriverAssignment assignment = outbox.getAssignment();
            eventPublisher.publishEvent(new AssignmentNotificationEvent(
                    outbox.getTenantId(),
                    assignment.getId(),
                    assignment.getBooking().getId(),
                    outbox.getAudience().name(),
                    outbox.getEventType()
            ));
            outbox.setStatus(AssignmentNotificationStatus.DELIVERED);
            outbox.setDeliveredAt(Instant.now());
            outbox.setLastError(null);
        } catch (RuntimeException exception) {
            int attempts = outbox.getAttempts() + 1;
            outbox.setAttempts(attempts);
            outbox.setLastError(exception.getClass().getSimpleName());
            if (attempts >= maxAttempts) {
                outbox.setStatus(AssignmentNotificationStatus.FAILED);
            } else {
                outbox.setNextAttemptAt(
                        Instant.now().plus(1L << Math.min(attempts, 10), ChronoUnit.MINUTES)
                );
            }
        }
    }
}
