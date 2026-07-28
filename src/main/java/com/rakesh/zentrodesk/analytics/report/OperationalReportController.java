package com.rakesh.zentrodesk.analytics.report;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/reports")
@PreAuthorize("hasAuthority('" + TenantPermissions.REPORT_READ + "')")
public class OperationalReportController {

    private final OperationalReportService reportService;

    @GetMapping
    public ApiResponse<OperationalReportResponse> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Operational reports retrieved successfully",
                reportService.summary(from, to, timezone, status),
                request.getRequestURI()
        );
    }

    @GetMapping("/{type}/details")
    public ApiResponse<PageResponse<ReportDetailRow>> details(
            @PathVariable ReportType type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Report details retrieved successfully",
                reportService.details(type, from, to, timezone, status, page, size),
                request.getRequestURI()
        );
    }

    @GetMapping(value = "/{type}/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('" + TenantPermissions.REPORT_EXPORT + "')")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable ReportType type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CurrentUser user
    ) {
        StreamingResponseBody body = outputStream -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            reportService.exportCsv(
                    type,
                    from,
                    to,
                    timezone,
                    status,
                    user.tenantId().orElseThrow(),
                    user.id(),
                    writer
            );
        };
        String filename = type.name().toLowerCase() + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
