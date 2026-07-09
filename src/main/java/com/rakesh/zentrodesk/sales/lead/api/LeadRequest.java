package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeadRequest(
        UUID assignedUserId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 100) String source,
        @Size(max = 150) String destination,
        LocalDate travelDate,
        Integer adults,
        Integer children,
        BigDecimal budget,
        LeadStatus status,
        String remarks,
        Boolean active
) {
}
