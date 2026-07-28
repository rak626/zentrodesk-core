package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

public record BookingRequest(
        @NotNull UUID customerId,
        UUID quotationId,
        UUID driverId,
        String bookingNumber,
        LocalDate travelStart,
        LocalDate travelEnd,
        BookingStatus status,
        String notes,
        Boolean active,
        BigDecimal agreedValue,
        String idempotencyKey,
        Long version
) {
    public BookingRequest(
            UUID customerId,
            UUID quotationId,
            UUID driverId,
            String bookingNumber,
            LocalDate travelStart,
            LocalDate travelEnd,
            BookingStatus status,
            String notes,
            Boolean active
    ) {
        this(
                customerId,
                quotationId,
                driverId,
                bookingNumber,
                travelStart,
                travelEnd,
                status,
                notes,
                active,
                null,
                null,
                null
        );
    }
}
