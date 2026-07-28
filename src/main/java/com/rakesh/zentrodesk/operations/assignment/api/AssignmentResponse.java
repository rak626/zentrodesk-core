package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID bookingId,
        String bookingNumber,
        UUID driverId,
        String driverName,
        UUID vehicleId,
        String vehicleRegistration,
        Instant pickupAt,
        Instant dropoffAt,
        String pickupLocation,
        String dropoffLocation,
        AssignmentStatus status,
        boolean active,
        boolean detailsVisible,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
