package com.rakesh.zentrodesk.communication.notification.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.communication.notification.NotificationStatus;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/notifications")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size,
                                                                   @RequestParam(defaultValue = "createdAt") String sort,
                                                                   @RequestParam(defaultValue = "DESC") String direction,
                                                                   @RequestParam(required = false) NotificationStatus status,
                                                                   @AuthenticationPrincipal CurrentUser user,
                                                                   HttpServletRequest servletRequest) {
        return ApiResponse.success("Notifications retrieved successfully", service.findAll(page, size, sort, direction, status, user), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) {
        return ApiResponse.success("Notification marked read", service.markRead(id, user), servletRequest.getRequestURI());
    }
}
