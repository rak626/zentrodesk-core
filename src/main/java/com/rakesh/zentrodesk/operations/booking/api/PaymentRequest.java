package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.PaymentMethod;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        @NotNull BigDecimal amount,
        @NotNull PaymentMethod method,
        PaymentStatus status,
        String transactionReference,
        Instant paidAt,
        String remarks,
        Boolean active
) {
}
