package com.rakesh.zentrodesk.operations.task.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.task.TaskStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/tasks")
@PreAuthorize("hasAuthority('" + TenantPermissions.TASK_READ + "')")
public class TaskController {

    private final TaskService service;

    @GetMapping
    public ApiResponse<PageResponse<TaskResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(defaultValue = "createdAt") String sort,
                                                           @RequestParam(defaultValue = "DESC") String direction,
                                                           @RequestParam(required = false) TaskStatus status,
                                                           @RequestParam(required = false) UUID userId,
                                                           @RequestParam(required = false) Instant from,
                                                           @RequestParam(required = false) Instant to,
                                                           HttpServletRequest servletRequest) {
        return ApiResponse.success("Tasks retrieved successfully", service.findAll(page, size, sort, direction, status, userId, from, to), servletRequest.getRequestURI());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.TASK_CREATE + "')")
    public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Task created successfully", service.create(request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.TASK_UPDATE + "')")
    public ApiResponse<TaskResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody TaskStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Task status updated successfully", service.updateStatus(id, request), servletRequest.getRequestURI());
    }
}
