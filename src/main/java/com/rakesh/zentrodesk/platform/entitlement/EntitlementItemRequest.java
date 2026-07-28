package com.rakesh.zentrodesk.platform.entitlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EntitlementItemRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$")
        String featureKey,
        boolean enabled
) {
}
