package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record QuotationItemRequest(
        @NotNull QuotationItemType type,
        @NotBlank @Size(max = 200) String description,
        @Size(max = 100) String unit,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        @Size(max = 2000) String remarks
) {
}
