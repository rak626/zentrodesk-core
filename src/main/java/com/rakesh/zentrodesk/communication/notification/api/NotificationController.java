package com.rakesh.zentrodesk.communication.notification.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.communication.notification.NotificationStatus;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/notifications")
@PreAuthorize("hasAuthority('" + TenantPermissions.NOTIFICATION_READ + "')")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size,
                                                                   @RequestParam(defaultValue = "createdAt") String sort,
                                                                   @RequestParam(defaultValue = "DESC") String direction,
                                                                   @RequestParam(required = false) NotificationStatus status,
                                                                   @RequestParam(required = false) String category,
                                                                   @AuthenticationPrincipal CurrentUser user,
                                                                   HttpServletRequest servletRequest) {
        return ApiResponse.success("Notifications retrieved successfully", service.findAll(page, size, sort, direction, status, category, user), servletRequest.getRequestURI());
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CurrentUser user,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Unread notification count retrieved",
                new UnreadCountResponse(service.unreadCount(user)),
                servletRequest.getRequestURI()
        );
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('" + TenantPermissions.NOTIFICATION_UPDATE + "')")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) {
        return ApiResponse.success("Notification marked read", service.markRead(id, user), servletRequest.getRequestURI());
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAuthority('" + TenantPermissions.NOTIFICATION_UPDATE + "')")
    public ApiResponse<MarkAllReadResponse> markAllRead(
            @AuthenticationPrincipal CurrentUser user,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Notifications marked read",
                new MarkAllReadResponse(service.markAllRead(user)),
                servletRequest.getRequestURI()
        );
    }

    public record UnreadCountResponse(long count) {
    }

    public record MarkAllReadResponse(int updated) {
    }
}
