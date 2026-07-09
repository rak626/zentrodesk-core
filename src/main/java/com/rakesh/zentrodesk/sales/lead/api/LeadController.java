package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/app/leads")
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    public ApiResponse<PageResponse<LeadResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) UUID assignedUserId,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Leads retrieved successfully", leadService.findAll(page, size, sort, direction, search, status, assignedUserId, active), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    public ApiResponse<LeadResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Lead retrieved successfully", leadService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<LeadResponse> create(@Valid @RequestBody LeadRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Lead created successfully", leadService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    public ApiResponse<LeadResponse> update(@PathVariable UUID id, @Valid @RequestBody LeadRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Lead updated successfully", leadService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<LeadResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody LeadStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Lead status updated successfully", leadService.updateStatus(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/assign")
    public ApiResponse<LeadResponse> assign(@PathVariable UUID id, @Valid @RequestBody LeadAssignRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Lead assigned successfully", leadService.assign(id, request), servletRequest.getRequestURI());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
        leadService.delete(id);
        return ApiResponse.success("Lead disabled successfully", null, servletRequest.getRequestURI());
    }
}
