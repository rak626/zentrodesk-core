package com.rakesh.zentrodesk.platform.invitation;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/invitations")
@PreAuthorize("hasAuthority('" + TenantPermissions.TEAM_READ + "')")
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping
    public ApiResponse<PageResponse<InvitationResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) InvitationStatus status,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Invitations retrieved successfully",
                invitationService.findAll(page, size, sort, direction, search, roleId, status),
                servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAuthority('" + TenantPermissions.TEAM_CREATE + "')")
    public ApiResponse<InvitationResponse> resend(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Invitation resent successfully", invitationService.resend(id),
                servletRequest.getRequestURI());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.TEAM_DELETE + "')")
    public ApiResponse<InvitationResponse> revoke(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Invitation revoked successfully", invitationService.revoke(id),
                servletRequest.getRequestURI());
    }
}
