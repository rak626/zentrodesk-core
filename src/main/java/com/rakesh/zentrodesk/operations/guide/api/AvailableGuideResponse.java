package com.rakesh.zentrodesk.operations.guide.api;

import com.rakesh.zentrodesk.operations.guide.GuideStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailableGuideResponse(
        UUID id,
        String name,
        String languages,
        BigDecimal baseRate,
        GuideStatus status,
        boolean available
) {
}
