package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.operations.driver.DriverStatus;

import java.util.UUID;

public record AvailableDriverResponse(
        UUID id,
        String name,
        UUID defaultVehicleId,
        DriverStatus status,
        boolean available
) {
}
