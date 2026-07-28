package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.sales.lead.FollowUpPriority;
import com.rakesh.zentrodesk.sales.lead.ReminderState;

import java.time.Instant;
import java.util.UUID;

public record FollowUpResponse(
        UUID id,
        UUID leadId,
        UUID ownerId,
        Instant dueAt,
        FollowUpPriority priority,
        String notes,
        String outcome,
        Instant completedAt,
        ReminderState reminderState,
        boolean overdue,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
