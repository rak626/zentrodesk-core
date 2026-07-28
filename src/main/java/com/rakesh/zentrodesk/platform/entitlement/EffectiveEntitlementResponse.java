package com.rakesh.zentrodesk.platform.entitlement;

public record EffectiveEntitlementResponse(
        String featureKey,
        boolean enabled
) {
}
