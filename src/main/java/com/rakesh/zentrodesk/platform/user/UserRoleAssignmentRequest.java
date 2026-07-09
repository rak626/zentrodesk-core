package com.rakesh.zentrodesk.platform.user;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserRoleAssignmentRequest(
        @NotNull UUID roleId
) {
}
