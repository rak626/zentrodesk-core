package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record BookingRequest(
        @NotNull UUID customerId,
        UUID quotationId,
        UUID driverId,
        @NotBlank String bookingNumber,
        LocalDate travelStart,
        LocalDate travelEnd,
        BookingStatus status,
        String notes,
        Boolean active
) {
}
