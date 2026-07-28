package com.rakesh.zentrodesk.operations.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentReversalRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank @Size(max = 500) String reason
) {
}
