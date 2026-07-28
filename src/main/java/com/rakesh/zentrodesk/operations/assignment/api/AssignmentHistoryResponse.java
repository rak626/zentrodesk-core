package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.operations.assignment.AssignmentAction;
import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AssignmentHistoryResponse(
        UUID id,
        AssignmentAction action,
        UUID previousDriverId,
        UUID driverId,
        UUID previousVehicleId,
        UUID vehicleId,
        AssignmentStatus previousStatus,
        AssignmentStatus status,
        String reason,
        UUID changedBy,
        Instant changedAt
) {
}
