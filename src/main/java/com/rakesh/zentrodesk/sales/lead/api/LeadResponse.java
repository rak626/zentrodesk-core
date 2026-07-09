package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.sales.lead.LeadStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        UUID assignedUserId,
        String name,
        String phone,
        String email,
        String source,
        String destination,
        LocalDate travelDate,
        Integer adults,
        Integer children,
        BigDecimal budget,
        LeadStatus status,
        String remarks,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
