package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String registrationNumber,
        String type,
        String make,
        String model,
        Integer capacity,
        VehicleStatus status,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
