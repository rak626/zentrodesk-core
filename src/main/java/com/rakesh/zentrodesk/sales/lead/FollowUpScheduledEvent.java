package com.rakesh.zentrodesk.sales.lead;

import java.time.Instant;
import java.util.UUID;

public record FollowUpScheduledEvent(
        UUID tenantId,
        UUID followUpId,
        UUID leadId,
        UUID ownerId,
        Instant dueAt,
        String notes
) {
}
