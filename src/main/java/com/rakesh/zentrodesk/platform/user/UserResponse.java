package com.rakesh.zentrodesk.platform.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        UUID roleId,
        String roleName,
        String firstName,
        String lastName,
        String email,
        String phone,
        Instant lastLoginAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
