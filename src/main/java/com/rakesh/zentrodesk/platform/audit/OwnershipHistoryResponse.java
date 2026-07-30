package com.rakesh.zentrodesk.platform.audit;

import java.time.Instant;
import java.util.UUID;

public record OwnershipHistoryResponse(
        UUID id,
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
