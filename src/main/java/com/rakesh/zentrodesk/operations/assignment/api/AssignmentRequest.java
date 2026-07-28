package com.rakesh.zentrodesk.operations.assignment.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record AssignmentRequest(
        @NotNull UUID bookingId,
        @NotNull UUID driverId,
        @NotNull UUID vehicleId,
        @NotNull @Future Instant pickupAt,
        @NotNull @Future Instant dropoffAt,
        @NotBlank @Size(max = 500) String pickupLocation,
        @NotBlank @Size(max = 500) String dropoffLocation,
        @NotBlank @Size(max = 100) String idempotencyKey
) {
}
