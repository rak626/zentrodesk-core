package com.rakesh.zentrodesk.analytics.search;

import java.util.UUID;

public record SearchResult(
        UUID id,
        String title,
        String subtitle,
        String status
) {
}
