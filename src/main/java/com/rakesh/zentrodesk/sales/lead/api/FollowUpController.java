package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
@PreAuthorize("hasAuthority('" + TenantPermissions.LEAD_READ + "')")
public class FollowUpController {

    private final FollowUpService followUpService;

    @GetMapping("/follow-ups")
    public ApiResponse<PageResponse<FollowUpResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dueAt") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) Boolean overdue,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Follow-ups retrieved successfully",
                followUpService.findAll(page, size, sort, direction, leadId, ownerId, completed, overdue),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/follow-ups/{id}")
    public ApiResponse<FollowUpResponse> findById(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Follow-up retrieved successfully",
                followUpService.findById(id),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/leads/{leadId}/timeline")
    public ApiResponse<PageResponse<LeadActivityResponse>> timeline(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Lead timeline retrieved successfully",
                followUpService.timeline(leadId, page, size),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/follow-ups")
    @PreAuthorize("hasAuthority('" + TenantPermissions.LEAD_UPDATE + "')")
    public ApiResponse<FollowUpResponse> create(
            @Valid @RequestBody FollowUpRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Follow-up created successfully",
                followUpService.create(request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/follow-ups/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.LEAD_UPDATE + "')")
    public ApiResponse<FollowUpResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FollowUpRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Follow-up updated successfully",
                followUpService.update(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PatchMapping("/follow-ups/{id}/complete")
    @PreAuthorize("hasAuthority('" + TenantPermissions.LEAD_UPDATE + "')")
    public ApiResponse<FollowUpResponse> complete(
            @PathVariable UUID id,
            @Valid @RequestBody FollowUpCompletionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Follow-up completed successfully",
                followUpService.complete(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/follow-ups/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.LEAD_UPDATE + "')")
    public ApiResponse<Void> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        followUpService.disable(id);
        return ApiResponse.success("Follow-up disabled successfully", null, servletRequest.getRequestURI());
    }
}
