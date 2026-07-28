package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.sales.lead.FollowUpPriority;
import com.rakesh.zentrodesk.sales.lead.ReminderState;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record FollowUpRequest(
        @NotNull UUID leadId,
        @NotNull UUID ownerId,
        @NotNull @FutureOrPresent Instant dueAt,
        FollowUpPriority priority,
        @Size(max = 1000) String notes,
        ReminderState reminderState,
        Boolean active,
        Long version
) {
}
