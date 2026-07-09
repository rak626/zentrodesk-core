package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
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
@RequestMapping("/api/platform/tenants")
public class PlatformTenantController {

    private final PlatformTenantService tenantService;

    @GetMapping
    public ApiResponse<PageResponse<PlatformTenantResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Tenants retrieved successfully",
                tenantService.findAll(page, size, sort, direction, search, active),
                servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    public ApiResponse<PlatformTenantResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Tenant retrieved successfully", tenantService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<PlatformTenantResponse> create(@Valid @RequestBody PlatformTenantCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Tenant created successfully", tenantService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<PlatformTenantResponse> update(@PathVariable UUID id, @Valid @RequestBody PlatformTenantUpdateRequest request,
                                                      HttpServletRequest servletRequest) {
        return ApiResponse.success("Tenant updated successfully", tenantService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/enable")
    public ApiResponse<PlatformTenantResponse> enable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Tenant enabled successfully", tenantService.enable(id), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<PlatformTenantResponse> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Tenant disabled successfully", tenantService.disable(id), servletRequest.getRequestURI());
    }
}
