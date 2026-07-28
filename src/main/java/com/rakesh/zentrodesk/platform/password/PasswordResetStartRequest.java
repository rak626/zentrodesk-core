package com.rakesh.zentrodesk.platform.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetStartRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 50) String tenantCode
) {
}
