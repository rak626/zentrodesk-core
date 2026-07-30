package com.rakesh.zentrodesk.communication.quotation.api;

import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentDeliveryStatus;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentGenerationStatus;

import java.time.Instant;
import java.util.UUID;

public record QuotationDocumentResponse(
        UUID id,
        UUID quotationId,
        int documentVersion,
        int templateVersion,
        String templateName,
        String fileName,
        Long contentLength,
        String contentSha256,
        QuotationDocumentGenerationStatus generationStatus,
        QuotationDocumentDeliveryStatus deliveryStatus,
        String providerMessageId,
        String lastError,
        Instant generatedAt,
        Instant deliveredAt,
        Instant createdAt
) {
}
