package com.rakesh.zentrodesk.operations.assignment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignmentReassignRequest(
        @NotNull UUID driverId,
        @NotNull UUID vehicleId,
        @NotNull Long version,
        @Size(max = 500) String reason
) {
}
