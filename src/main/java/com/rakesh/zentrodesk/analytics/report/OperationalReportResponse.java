package com.rakesh.zentrodesk.analytics.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OperationalReportResponse(
        LocalDate from,
        LocalDate to,
        String timezone,
        Instant generatedAt,
        List<ReportSection> sections
) {

    public record ReportSection(
            ReportType type,
            String label,
            Map<String, BigDecimal> metrics,
            List<ReportRow> rows
    ) {
    }

    public record ReportRow(
            String key,
            String label,
            long count,
            BigDecimal amount,
            BigDecimal rate
    ) {
    }
}
