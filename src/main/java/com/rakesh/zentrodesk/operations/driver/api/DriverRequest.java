package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 20) String phone,
        @Size(max = 100) String licenseNumber,
        @Size(max = 100) String vehicleNumber,
        @Size(max = 100) String vehicleType,
        DriverStatus status,
        Boolean active
) {
}
