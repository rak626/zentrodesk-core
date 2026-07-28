package com.rakesh.zentrodesk.sales.customer.api;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String country,
        String notes,
        boolean active,
        long version,
        CustomerRelationshipSummary relationships,
        Instant createdAt,
        Instant updatedAt
) {
}
