package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.platform.user.PlatformRole;

import java.time.Instant;
import java.util.UUID;

public record PlatformUserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        PlatformRole role,
        Instant lastLoginAt,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
