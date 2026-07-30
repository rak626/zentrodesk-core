package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationRequest(
        UUID leadId,
        UUID customerId,
        @NotNull LocalDate travelStart,
        @NotNull LocalDate travelEnd,
        @Min(0) Integer adults,
        @Min(0) Integer children,
        @DecimalMin("0.00") BigDecimal discount,
        @DecimalMin("0.00") BigDecimal tax,
        LocalDate validUntil,
        QuotationStatus status,
        @Size(max = 1000) String notes,
        Boolean active,
        Long version,
        @NotEmpty List<@Valid QuotationItemRequest> items
) {
}
