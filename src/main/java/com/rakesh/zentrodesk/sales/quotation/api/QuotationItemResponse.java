package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationItemType;
import com.rakesh.zentrodesk.sales.quotation.QuotationPricingBasis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationItemResponse(
        UUID id,
        int position,
        QuotationItemType type,
        QuotationPricingBasis pricingBasis,
        String description,
        String unit,
        int quantity,
        BigDecimal unitPrice,
        Integer chargeablePeople,
        boolean chargeablePeopleOverridden,
        Integer vehicleCapacity,
        Integer vehicleCount,
        boolean vehicleCountOverridden,
        LocalDate checkIn,
        LocalDate checkOut,
        Instant startsAt,
        Instant endsAt,
        String pickupLocation,
        String dropoffLocation,
        boolean capacityOverridden,
        List<UUID> resourceIds,
        BigDecimal amount,
        String remarks
) {
}
