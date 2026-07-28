package com.rakesh.zentrodesk.operations.booking.api;

import jakarta.validation.constraints.Size;

public record BookingConversionRequest(
        @Size(max = 100) String idempotencyKey,
        @Size(max = 2000) String notes
) {
}
