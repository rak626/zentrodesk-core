package com.rakesh.zentrodesk.inventory.property.api;

import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PropertyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull PropertyType type,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 100) String contactPerson,
        PropertyStatus status,
        Boolean active,
        Long version
) {
}
