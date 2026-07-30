package com.rakesh.zentrodesk.operations.guide.api;

import com.rakesh.zentrodesk.operations.guide.GuideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GuideResponse(
        UUID id,
        String name,
        String phone,
        String languages,
        BigDecimal baseRate,
        GuideStatus status,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
