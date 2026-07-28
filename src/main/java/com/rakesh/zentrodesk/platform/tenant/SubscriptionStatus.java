package com.rakesh.zentrodesk.platform.tenant;

public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    CANCELLED;

    public boolean allowsPaidFeatures() {
        return this == TRIAL || this == ACTIVE;
    }
}
