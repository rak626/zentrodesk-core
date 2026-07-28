package com.rakesh.zentrodesk.analytics.search;

import java.util.List;

public record SearchGroupResponse(
        SearchGroup group,
        List<SearchResult> results
) {
}
