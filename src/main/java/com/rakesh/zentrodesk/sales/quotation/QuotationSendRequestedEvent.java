package com.rakesh.zentrodesk.sales.quotation;

import java.time.Instant;
import java.util.UUID;

public record QuotationSendRequestedEvent(
        UUID tenantId,
        UUID quotationId,
        Instant occurredAt
) {
}
