package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.driver.VehicleStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
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
@RequestMapping("/api/app/vehicles")
@PreAuthorize("hasAuthority('" + TenantPermissions.DRIVER_READ + "')")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public ApiResponse<PageResponse<VehicleResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Vehicles retrieved successfully",
                vehicleService.findAll(page, size, sort, direction, search, status, active),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<VehicleResponse> findById(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Vehicle retrieved successfully",
                vehicleService.findById(id),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.DRIVER_CREATE + "')")
    public ApiResponse<VehicleResponse> create(
            @Valid @RequestBody VehicleRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Vehicle created successfully",
                vehicleService.create(request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.DRIVER_UPDATE + "')")
    public ApiResponse<VehicleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Vehicle updated successfully",
                vehicleService.update(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.DRIVER_UPDATE + "')")
    public ApiResponse<VehicleResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Vehicle status updated successfully",
                vehicleService.updateStatus(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.DRIVER_DELETE + "')")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        vehicleService.delete(id);
        return ApiResponse.success(
                "Vehicle disabled successfully",
                null,
                servletRequest.getRequestURI()
        );
    }
}
