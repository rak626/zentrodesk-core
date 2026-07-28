package com.rakesh.zentrodesk.sales.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 2000) String notes,
        Boolean active,
        Long version
) {
}
