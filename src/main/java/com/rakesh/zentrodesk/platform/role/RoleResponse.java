package com.rakesh.zentrodesk.platform.role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        String name,
        String description,
        boolean active,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt
) {
}
