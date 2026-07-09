package com.rakesh.zentrodesk.platform.role;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.user.UserResponse;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/roles")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ApiResponse<PageResponse<RoleResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Roles retrieved successfully",
                roleService.findAll(page, size, sort, direction, search, active),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Role retrieved successfully", roleService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Role created successfully", roleService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Role updated successfully", roleService.update(id, request), servletRequest.getRequestURI());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
        roleService.delete(id);
        return ApiResponse.success("Role deleted successfully", null, servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<RoleResponse> assignPermissions(@PathVariable UUID id, @Valid @RequestBody RolePermissionRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Role permissions assigned successfully", roleService.assignPermissions(id, request), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}/users")
    public ApiResponse<PageResponse<UserResponse>> users(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Role users retrieved successfully",
                roleService.users(id, page, size, sort, direction, active),
                servletRequest.getRequestURI()
        );
    }
}
