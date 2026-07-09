package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import jakarta.validation.constraints.NotNull;

public record DriverStatusRequest(@NotNull DriverStatus status) {
}
