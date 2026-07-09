package com.rakesh.zentrodesk.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email @NotBlank @Size(max = 150) String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @Size(max = 50) String tenantCode
) {
}
