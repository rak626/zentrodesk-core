package com.rakesh.zentrodesk.common.pagination;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.Set;

public final class PaginationUtils {

    private static final int MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    public static Pageable pageable(int page, int size, String sortBy, String direction, Set<String> allowedSorts) {
        if (page < 0) {
            throw new BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String resolvedSortBy = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        if (!allowedSorts.contains(resolvedSortBy)) {
            throw new BadRequestException("Unsupported sort field: " + resolvedSortBy);
        }
        Sort.Direction resolvedDirection = Sort.Direction.fromOptionalString(direction)
                .orElse(Sort.Direction.DESC);
        return PageRequest.of(page, size, Sort.by(resolvedDirection, resolvedSortBy));
    }
}
