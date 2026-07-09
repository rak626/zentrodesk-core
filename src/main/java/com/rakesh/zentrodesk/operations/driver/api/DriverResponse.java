package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.DriverStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverResponse(UUID id, String name, String phone, String licenseNumber, String vehicleNumber,
                             String vehicleType, DriverStatus status, boolean active, Instant createdAt, Instant updatedAt) {
}
