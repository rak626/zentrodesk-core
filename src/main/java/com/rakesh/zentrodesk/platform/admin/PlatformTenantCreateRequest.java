package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.platform.tenant.SubscriptionStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PlatformTenantCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String code,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 50) String timezone,
        @Size(max = 10) String currency,
        @NotBlank @Size(max = 100) String adminFirstName,
        @Size(max = 100) String adminLastName,
        @NotBlank @Email @Size(max = 150) String adminEmail,
        @Size(max = 20) String adminPhone,
        SubscriptionStatus subscriptionStatus,
        @Size(max = 50) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$") String planCode,
        @Positive Integer seatLimit
) {
}
