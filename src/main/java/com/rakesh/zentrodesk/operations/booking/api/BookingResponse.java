package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID customerId,
        UUID quotationId,
        UUID driverId,
        String bookingNumber,
        LocalDate travelStart,
        LocalDate travelEnd,
        BookingStatus status,
        String notes,
        boolean active,
        BigDecimal paidAmount,
        BigDecimal totalPaid,
        PaymentStatus paymentStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
