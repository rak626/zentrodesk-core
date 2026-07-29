package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/conversations")
@PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_READ + "')")
public class CustomerConversationController {

    private final CustomerConversationService service;

    @GetMapping
    public ApiResponse<PageResponse<CustomerConversationResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "lastMessageAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) CustomerConversationStatus status,
            @RequestParam(required = false) UUID assignedUserId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Conversations retrieved successfully",
                service.findAll(page, size, sort, direction, status, assignedUserId),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_CREATE + "')")
    public ApiResponse<CustomerConversationResponse> create(
            @Valid @RequestBody CustomerConversationCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Conversation created successfully",
                service.create(request),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<ConversationMessageResponse>> messages(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Conversation messages retrieved successfully",
                service.messages(id),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_CREATE + "')")
    public ApiResponse<ConversationMessageResponse> send(
            @PathVariable UUID id,
            @Valid @RequestBody ConversationSendRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Message queued successfully",
                service.send(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_ASSIGN + "')")
    public ApiResponse<CustomerConversationResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody ConversationAssignmentRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Conversation assignments updated successfully",
                service.assign(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/read")
    public ApiResponse<CustomerConversationResponse> markRead(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Conversation marked read",
                service.markRead(id),
                servletRequest.getRequestURI()
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_UPDATE + "')")
    public ApiResponse<CustomerConversationResponse> updateState(
            @PathVariable UUID id,
            @Valid @RequestBody ConversationStateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Conversation status updated successfully",
                service.updateState(id, request),
                servletRequest.getRequestURI()
        );
    }
}
