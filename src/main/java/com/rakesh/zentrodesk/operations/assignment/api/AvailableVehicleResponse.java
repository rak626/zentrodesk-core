package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.operations.driver.VehicleStatus;

import java.util.UUID;

public record AvailableVehicleResponse(
        UUID id,
        String registrationNumber,
        String type,
        VehicleStatus status,
        boolean available
) {
}
