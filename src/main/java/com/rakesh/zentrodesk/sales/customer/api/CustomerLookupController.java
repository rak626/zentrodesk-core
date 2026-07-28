package com.rakesh.zentrodesk.sales.customer.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/customers/lookup")
@PreAuthorize("hasAuthority('" + TenantPermissions.CUSTOMER_READ + "')")
public class CustomerLookupController {

    private final CustomerLookupService customerLookupService;

    @GetMapping
    public ApiResponse<List<CustomerLookupResponse>> find(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "50") @Min(1) @Max(50) int limit,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Customer lookup retrieved successfully",
                customerLookupService.find(query, limit),
                servletRequest.getRequestURI()
        );
    }
}
