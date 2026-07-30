package com.rakesh.zentrodesk.operations.guide.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.guide.GuideStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/guides")
@PreAuthorize("hasAuthority('" + TenantPermissions.GUIDE_READ + "') or hasRole('OWNER')")
public class GuideController {

    private final GuideService guideService;

    @GetMapping
    public ApiResponse<PageResponse<GuideResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) GuideStatus status,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Guides retrieved successfully",
                guideService.findAll(page, size, sort, direction, search, status, active),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/availability")
    public ApiResponse<GuideAvailabilityResponse> availability(
            @RequestParam Instant from,
            @RequestParam Instant to,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Guide availability retrieved successfully",
                guideService.availability(from, to),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.GUIDE_CREATE + "') or hasRole('OWNER')")
    public ApiResponse<GuideResponse> create(
            @Valid @RequestBody GuideRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Guide created successfully",
                guideService.create(request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.GUIDE_UPDATE + "') or hasRole('OWNER')")
    public ApiResponse<GuideResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody GuideRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Guide updated successfully",
                guideService.update(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.GUIDE_DELETE + "') or hasRole('OWNER')")
    public ApiResponse<Void> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        guideService.disable(id);
        return ApiResponse.success("Guide disabled successfully", null, servletRequest.getRequestURI());
    }
}
