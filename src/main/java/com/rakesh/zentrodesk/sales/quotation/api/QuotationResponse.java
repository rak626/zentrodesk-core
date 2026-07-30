package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationResponse(
        UUID id,
        UUID leadId,
        UUID customerId,
        UUID assignedUserId,
        String assignedUserName,
        String quotationNumber,
        UUID seriesId,
        int revisionNumber,
        UUID revisedFromId,
        boolean superseded,
        LocalDate travelStart,
        LocalDate travelEnd,
        Integer adults,
        Integer children,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal perPersonTotal,
        LocalDate validUntil,
        QuotationStatus status,
        String notes,
        boolean active,
        long version,
        List<QuotationItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
