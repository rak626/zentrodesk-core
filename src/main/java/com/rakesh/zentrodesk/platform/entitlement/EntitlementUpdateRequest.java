package com.rakesh.zentrodesk.platform.entitlement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EntitlementUpdateRequest(
        @NotEmpty @Size(max = 100) List<@Valid EntitlementItemRequest> entitlements
) {
}
