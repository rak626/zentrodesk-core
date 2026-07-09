package com.rakesh.zentrodesk.platform.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RolePermissionRequest(
        @NotEmpty Set<@NotBlank @Size(max = 100) String> permissions
) {
}
