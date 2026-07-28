package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AssignmentWindowResponse(
        UUID id,
        UUID bookingId,
        UUID driverId,
        UUID vehicleId,
        Instant pickupAt,
        Instant dropoffAt,
        AssignmentStatus status
) {
}
