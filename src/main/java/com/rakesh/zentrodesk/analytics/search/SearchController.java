package com.rakesh.zentrodesk.analytics.search;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ApiResponse<SearchResponse> search(
            @RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) Set<SearchGroup> groups,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        String normalizedQuery = q.trim();
        if (normalizedQuery.length() < 2 || normalizedQuery.length() > 100) {
            throw new IllegalArgumentException("Search query length is invalid");
        }
        return ApiResponse.success(
                "Search completed successfully",
                searchService.search(normalizedQuery, groups, limit, authentication.getAuthorities()),
                servletRequest.getRequestURI()
        );
    }
}
