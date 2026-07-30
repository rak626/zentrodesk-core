package com.rakesh.zentrodesk.sales.quotation.api;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record QuotationItemScheduleRequest(
        LocalDate checkIn,
        LocalDate checkOut,
        Instant startsAt,
        Instant endsAt,
        @Size(max = 500) String pickupLocation,
        @Size(max = 500) String dropoffLocation
) {
}
