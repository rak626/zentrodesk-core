package com.rakesh.zentrodesk.operations.assignment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentNotificationService {

    private final AssignmentNotificationOutboxRepository repository;

    public void enqueue(DriverAssignment assignment, AssignmentAction action) {
        List<AssignmentNotificationOutbox> messages = List.of(
                message(assignment, action, AssignmentNotificationAudience.CUSTOMER),
                message(assignment, action, AssignmentNotificationAudience.OPERATIONS)
        );
        repository.saveAll(messages);
    }

    private AssignmentNotificationOutbox message(
            DriverAssignment assignment,
            AssignmentAction action,
            AssignmentNotificationAudience audience
    ) {
        return AssignmentNotificationOutbox.builder()
                .tenantId(assignment.getTenantId())
                .assignment(assignment)
                .audience(audience)
                .eventType(action)
                .status(AssignmentNotificationStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(Instant.now())
                .build();
    }
}
