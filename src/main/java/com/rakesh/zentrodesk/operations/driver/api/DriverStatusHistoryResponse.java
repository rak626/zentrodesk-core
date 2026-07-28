package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.DriverStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverStatusHistoryResponse(
        UUID id,
        DriverStatus previousStatus,
        DriverStatus status,
        String reason,
        UUID changedBy,
        Instant changedAt
) {
}
