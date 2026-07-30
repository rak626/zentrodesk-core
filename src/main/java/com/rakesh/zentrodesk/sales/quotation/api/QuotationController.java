package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/app/quotations")
@PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_READ + "')")
public class QuotationController {

    private final QuotationService quotationService;

    @GetMapping
    public ApiResponse<PageResponse<QuotationResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotations retrieved successfully",
                quotationService.findAll(page, size, sort, direction, search, status, leadId, customerId, active),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<QuotationResponse> findById(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation retrieved successfully",
                quotationService.findById(id),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_CREATE + "')")
    public ApiResponse<QuotationResponse> create(
            @Valid @RequestBody QuotationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation created successfully",
                quotationService.create(request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_CREATE + "')")
    public ApiResponse<QuotationResponse> createRevision(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation revision created successfully",
                quotationService.createRevision(id),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_UPDATE + "')")
    public ApiResponse<QuotationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody QuotationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation updated successfully",
                quotationService.update(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_UPDATE + "')")
    public ApiResponse<QuotationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody QuotationStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation status updated successfully",
                quotationService.updateStatus(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<QuotationResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody QuotationAssignRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation assigned successfully",
                quotationService.assign(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_DELETE + "')")
    public ApiResponse<Void> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        quotationService.disable(id);
        return ApiResponse.success("Quotation disabled successfully", null, servletRequest.getRequestURI());
    }
}
