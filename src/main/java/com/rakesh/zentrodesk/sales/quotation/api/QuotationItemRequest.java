package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationItemType;
import com.rakesh.zentrodesk.sales.quotation.QuotationPricingBasis;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuotationItemRequest(
        @NotNull QuotationItemType type,
        QuotationPricingBasis pricingBasis,
        @NotBlank @Size(max = 200) String description,
        @Size(max = 100) String unit,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        @Positive Integer chargeablePeople,
        @Positive Integer vehicleCapacity,
        @Positive Integer vehicleCount,
        @Valid QuotationItemScheduleRequest schedule,
        List<UUID> resourceIds,
        Boolean capacityOverride,
        @Size(max = 2000) String remarks
) {
}
