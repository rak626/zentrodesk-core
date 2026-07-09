package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.user.PasswordResetRequest;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/platform/users")
public class PlatformUserController {

    private final PlatformUserService userService;

    @GetMapping
    public ApiResponse<PageResponse<PlatformUserResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PlatformRole role,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Platform users retrieved successfully",
                userService.findAll(page, size, sort, direction, search, role, enabled),
                servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    public ApiResponse<PlatformUserResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Platform user retrieved successfully", userService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<PlatformUserResponse> create(@Valid @RequestBody PlatformUserCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Platform user created successfully", userService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<PlatformUserResponse> update(@PathVariable UUID id, @Valid @RequestBody PlatformUserUpdateRequest request,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success("Platform user updated successfully", userService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/enable")
    public ApiResponse<PlatformUserResponse> enable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Platform user enabled successfully", userService.enable(id), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<PlatformUserResponse> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Platform user disabled successfully", userService.disable(id), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        userService.resetPassword(id, request);
        return ApiResponse.success("Password reset successfully", null, servletRequest.getRequestURI());
    }
}
