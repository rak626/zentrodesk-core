package com.rakesh.zentrodesk.analytics.dashboard;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/dashboard")
@PreAuthorize("hasAuthority('" + TenantPermissions.DASHBOARD_READ + "')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String timezone,
            @AuthenticationPrincipal CurrentUser user,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePrivate())
                .varyBy("Authorization", "Cookie")
                .body(ApiResponse.success(
                        "Dashboard summary retrieved successfully",
                        dashboardService.summary(from, to, timezone, user),
                        servletRequest.getRequestURI()
                ));
    }
}
