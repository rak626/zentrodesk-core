package com.rakesh.zentrodesk.platform.entitlement;

import java.time.Instant;

public record EntitlementResponse(
        String featureKey,
        boolean enabled,
        boolean effective,
        Instant createdAt,
        Instant updatedAt
) {
}
