package com.rakesh.zentrodesk.communication.quotation;

import java.time.Instant;
import java.util.UUID;

public record QuotationDocumentReadyEvent(
        UUID documentId,
        Instant occurredAt
) {
}
