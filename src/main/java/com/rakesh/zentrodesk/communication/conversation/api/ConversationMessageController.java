package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/conversations/messages")
@PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_READ + "')")
public class ConversationMessageController {

    private final ConversationMessageService service;

    @GetMapping
    public ApiResponse<PageResponse<ConversationMessageResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size,
                                                                          @RequestParam(defaultValue = "createdAt") String sort,
                                                                          @RequestParam(defaultValue = "DESC") String direction,
                                                                          @RequestParam(required = false) String phone,
                                                                          @RequestParam(required = false) UUID customerId,
                                                                          @RequestParam(required = false) ConversationMessageStatus status,
                                                                          HttpServletRequest servletRequest) {
        return ApiResponse.success("Conversation messages retrieved successfully", service.findAll(page, size, sort, direction, phone, customerId, status), servletRequest.getRequestURI());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_CREATE + "')")
    public ApiResponse<ConversationMessageResponse> create(@Valid @RequestBody ConversationMessageRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Conversation message created successfully", service.create(request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.CONVERSATION_UPDATE + "')")
    public ApiResponse<ConversationMessageResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody ConversationStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Conversation status updated successfully", service.updateStatus(id, request), servletRequest.getRequestURI());
    }
}
