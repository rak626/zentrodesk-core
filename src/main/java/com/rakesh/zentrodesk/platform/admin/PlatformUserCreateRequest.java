package com.rakesh.zentrodesk.platform.admin;

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
        @NotBlank @Size(min = 8, max = 100) String password,
        Boolean enabled
) {
}
