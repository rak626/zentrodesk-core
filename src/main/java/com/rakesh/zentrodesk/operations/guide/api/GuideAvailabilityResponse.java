package com.rakesh.zentrodesk.operations.guide.api;

import java.time.Instant;
import java.util.List;

public record GuideAvailabilityResponse(
        Instant from,
        Instant to,
        List<AvailableGuideResponse> guides
) {
}
