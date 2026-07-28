package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record QuotationItemResponse(
        UUID id,
        int position,
        QuotationItemType type,
        String description,
        String unit,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String remarks
) {
}
