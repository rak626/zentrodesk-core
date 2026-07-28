package com.rakesh.zentrodesk.operations.task;

import com.rakesh.zentrodesk.sales.lead.FollowUpScheduledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FollowUpTaskListener {

    private static final String SOURCE_TYPE = "LEAD_FOLLOW_UP";

    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void createTask(FollowUpScheduledEvent event) {
        if (taskRepository.findByTenantIdAndSourceTypeAndSourceIdAndActiveTrue(
                        event.tenantId(),
                        SOURCE_TYPE,
                        event.followUpId()
                )
                .isPresent()) {
            return;
        }
        try {
            taskRepository.saveAndFlush(Task.builder()
                    .tenantId(event.tenantId())
                    .userId(event.ownerId())
                    .title("Follow up lead " + event.leadId())
                    .description(event.notes())
                    .dueAt(event.dueAt())
                    .status(TaskStatus.PENDING)
                    .sourceType(SOURCE_TYPE)
                    .sourceId(event.followUpId())
                    .active(true)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // A retry or concurrent event already created the task.
        }
    }
}
