package com.rakesh.zentrodesk.sales.customer.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
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
@RequestMapping("/api/app/customers")
@PreAuthorize("hasAuthority('" + TenantPermissions.CUSTOMER_READ + "')")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Customers retrieved successfully",
                customerService.findAll(page, size, sort, direction, search, active),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/duplicates")
    public ApiResponse<CustomerDuplicateResponse> duplicates(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Customer duplicates checked",
                customerService.duplicates(phone, email),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> findById(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Customer retrieved successfully",
                customerService.findById(id),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.CUSTOMER_CREATE + "')")
    public ApiResponse<CustomerResponse> create(
            @Valid @RequestBody CustomerRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Customer created successfully",
                customerService.create(request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.CUSTOMER_UPDATE + "')")
    public ApiResponse<CustomerResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Customer updated successfully",
                customerService.update(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.CUSTOMER_DELETE + "')")
    public ApiResponse<Void> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        customerService.disable(id);
        return ApiResponse.success("Customer disabled successfully", null, servletRequest.getRequestURI());
    }
}
