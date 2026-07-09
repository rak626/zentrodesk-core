package com.rakesh.zentrodesk.platform.admin;

import java.time.Instant;
import java.util.UUID;

public record PlatformTenantResponse(
        UUID id,
        String name,
        String code,
        String email,
        String phone,
        String address,
        String city,
        String state,
        String country,
        String timezone,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
