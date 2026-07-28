package com.rakesh.zentrodesk.platform.entitlement;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EntitlementController {

    private final FeatureEntitlementService entitlementService;

    @GetMapping("/api/app/entitlements")
    public ApiResponse<List<EffectiveEntitlementResponse>> findCurrent(HttpServletRequest servletRequest) {
        return ApiResponse.success("Entitlements retrieved successfully",
                entitlementService.findForCurrentTenant(), servletRequest.getRequestURI());
    }

    @GetMapping("/api/platform/tenants/{tenantId}/entitlements")
    public ApiResponse<List<EntitlementResponse>> findForPlatform(
            @PathVariable UUID tenantId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Entitlements retrieved successfully",
                entitlementService.findForPlatform(tenantId), servletRequest.getRequestURI());
    }

    @PutMapping("/api/platform/tenants/{tenantId}/entitlements")
    public ApiResponse<List<EntitlementResponse>> updateForPlatform(
            @PathVariable UUID tenantId,
            @Valid @RequestBody EntitlementUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Entitlements updated successfully",
                entitlementService.updateForPlatform(tenantId, request), servletRequest.getRequestURI());
    }
}
