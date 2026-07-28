package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.VehicleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(max = 100) String registrationNumber,
        @NotBlank @Size(max = 100) String type,
        @Size(max = 100) String make,
        @Size(max = 100) String model,
        @Positive Integer capacity,
        VehicleStatus status,
        Boolean active,
        Long version
) {
}
