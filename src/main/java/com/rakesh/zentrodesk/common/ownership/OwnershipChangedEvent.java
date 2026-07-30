package com.rakesh.zentrodesk.common.ownership;

import java.time.Instant;
import java.util.UUID;

public record OwnershipChangedEvent(
        UUID tenantId,
        UUID previousOwnerId,
        UUID ownerId,
        UUID actorId,
        String reason,
        String sourceType,
        UUID sourceId,
        UUID leadId,
        UUID quotationId,
        UUID bookingId,
        UUID conversationId,
        UUID customerId,
        Instant occurredAt
) {
}
