package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.PaymentMethod;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull PaymentMethod method,
        PaymentStatus status,
        String transactionReference,
        Instant paidAt,
        String remarks,
        Boolean active,
        @Size(max = 100) String idempotencyKey
) {
}
