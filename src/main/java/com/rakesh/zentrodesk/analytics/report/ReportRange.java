package com.rakesh.zentrodesk.analytics.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

record ReportRange(
        LocalDate from,
        LocalDate to,
        ZoneId zone,
        Instant rangeStart,
        Instant rangeEnd
) {
}
