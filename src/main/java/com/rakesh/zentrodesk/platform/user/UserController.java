package com.rakesh.zentrodesk.platform.user;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
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
@RequestMapping("/api/app/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Users retrieved successfully",
                userService.findAll(page, size, sort, direction, search, roleId, active),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("User retrieved successfully", userService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("User created successfully", userService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("User updated successfully", userService.update(id, request), servletRequest.getRequestURI());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
        userService.delete(id);
        return ApiResponse.success("User deleted successfully", null, servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/enable")
    public ApiResponse<UserResponse> enable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("User enabled successfully", userService.enable(id), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<UserResponse> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("User disabled successfully", userService.disable(id), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/roles")
    public ApiResponse<UserResponse> assignRole(@PathVariable UUID id, @Valid @RequestBody UserRoleAssignmentRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("User role assigned successfully", userService.assignRole(id, request), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        userService.resetPassword(id, request);
        return ApiResponse.success("Password reset successfully", null, servletRequest.getRequestURI());
    }
}
