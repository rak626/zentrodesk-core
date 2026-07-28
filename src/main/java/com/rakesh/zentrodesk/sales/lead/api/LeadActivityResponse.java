package com.rakesh.zentrodesk.sales.lead.api;

import java.time.Instant;
import java.util.UUID;

public record LeadActivityResponse(
        UUID id,
        UUID actorId,
        String activityType,
        String summary,
        Instant occurredAt
) {
}
