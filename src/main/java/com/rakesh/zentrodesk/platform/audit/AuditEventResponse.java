package com.rakesh.zentrodesk.platform.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID actorId,
        String entityType,
        UUID entityId,
        String action,
        Instant occurredAt,
        String correlationId,
        String beforeSummary,
        String afterSummary
) {
}
