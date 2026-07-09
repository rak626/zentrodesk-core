package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/drivers")
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    public ApiResponse<PageResponse<DriverResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(defaultValue = "createdAt") String sort,
                                                             @RequestParam(defaultValue = "DESC") String direction,
                                                             @RequestParam(required = false) String search,
                                                             @RequestParam(required = false) DriverStatus status,
                                                             @RequestParam(required = false) Boolean active,
                                                             HttpServletRequest servletRequest) {
        return ApiResponse.success("Drivers retrieved successfully", driverService.findAll(page, size, sort, direction, search, status, active), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    public ApiResponse<DriverResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Driver retrieved successfully", driverService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<DriverResponse> create(@Valid @RequestBody DriverRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Driver created successfully", driverService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<DriverResponse> update(@PathVariable UUID id, @Valid @RequestBody DriverRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Driver updated successfully", driverService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DriverResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody DriverStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Driver status updated successfully", driverService.updateStatus(id, request), servletRequest.getRequestURI());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
        driverService.delete(id);
        return ApiResponse.success("Driver disabled successfully", null, servletRequest.getRequestURI());
    }
}
