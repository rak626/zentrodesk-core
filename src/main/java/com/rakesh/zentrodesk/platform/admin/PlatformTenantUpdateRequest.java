package com.rakesh.zentrodesk.platform.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformTenantUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String code,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 50) String timezone,
        @Size(max = 10) String currency,
        Boolean active
) {
}
