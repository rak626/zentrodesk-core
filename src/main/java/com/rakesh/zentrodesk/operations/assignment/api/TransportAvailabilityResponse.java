package com.rakesh.zentrodesk.operations.assignment.api;

import java.time.Instant;
import java.util.List;

public record TransportAvailabilityResponse(
        Instant from,
        Instant to,
        List<AvailableDriverResponse> drivers,
        List<AvailableVehicleResponse> vehicles,
        List<AssignmentWindowResponse> assignments
) {
}
