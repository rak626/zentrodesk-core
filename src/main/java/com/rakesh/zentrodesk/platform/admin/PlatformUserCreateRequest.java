package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.platform.auth.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlatformUserCreateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull PlatformRole role,
        @StrongPassword
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 100,
                pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$")
        String password,
        Boolean enabled
) {
}
