package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.PaymentMethod;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String transactionReference,
        Instant paidAt,
        String remarks,
        boolean active,
        String idempotencyKey,
        UUID reversalOfPaymentId,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
