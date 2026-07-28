package com.rakesh.zentrodesk.analytics.search;

import java.util.List;

public record SearchResponse(List<SearchGroupResponse> groups) {
}
