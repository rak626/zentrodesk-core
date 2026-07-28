package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DriverStatusRequest(
        @NotNull DriverStatus status,
        @Size(max = 500) String reason,
        Long version
) {

    public DriverStatusRequest(DriverStatus status) {
        this(status, null, null);
    }
}
