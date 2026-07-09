package com.rakesh.zentrodesk.inventory.property.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/properties")
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public ApiResponse<PageResponse<PropertyResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               @RequestParam(defaultValue = "createdAt") String sort,
                                                               @RequestParam(defaultValue = "DESC") String direction,
                                                               @RequestParam(required = false) String search,
                                                               @RequestParam(required = false) PropertyType type,
                                                               @RequestParam(required = false) PropertyStatus status,
                                                               @RequestParam(required = false) Boolean active,
                                                               HttpServletRequest servletRequest) {
        return ApiResponse.success("Properties retrieved successfully", propertyService.findAll(page, size, sort, direction, search, type, status, active), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    public ApiResponse<PropertyResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Property retrieved successfully", propertyService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<PropertyResponse> create(@Valid @RequestBody PropertyRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Property created successfully", propertyService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<PropertyResponse> update(@PathVariable UUID id, @Valid @RequestBody PropertyRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Property updated successfully", propertyService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PropertyResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody PropertyStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Property status updated successfully", propertyService.updateStatus(id, request), servletRequest.getRequestURI());
    }
}
