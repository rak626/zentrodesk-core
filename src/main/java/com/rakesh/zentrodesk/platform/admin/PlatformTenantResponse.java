package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.platform.tenant.SubscriptionStatus;

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
        SubscriptionStatus subscriptionStatus,
        String planCode,
        int seatLimit,
        Instant createdAt,
        Instant updatedAt
) {
}
