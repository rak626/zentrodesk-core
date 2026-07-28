package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.VehicleStatus;
import jakarta.validation.constraints.NotNull;

public record VehicleStatusRequest(@NotNull VehicleStatus status, Long version) {
}
