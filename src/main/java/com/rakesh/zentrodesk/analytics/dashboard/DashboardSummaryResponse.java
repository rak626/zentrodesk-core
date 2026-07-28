package com.rakesh.zentrodesk.analytics.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardSummaryResponse(
        LocalDate date,
        String timezone,
        String roleName,
        Instant refreshedAt,
        List<String> visibleWidgets,
        List<ActionLink> actions,
        List<KpiCard> kpis,
        List<TaskQueueItem> taskQueue,
        List<PaymentWatchItem> paymentWatch,
        List<AllocationWarningItem> allocationWarnings,
        List<ActivityItem> activityFeed,
        Map<String, Long> leadPipelineCounts,
        Map<String, String> widgetErrors
) {

    public record KpiCard(String label, String value, String detail) {
    }

    public record ActionLink(String label, String href, String permission) {
    }

    public record TaskQueueItem(UUID id, String title, String status, Instant dueAt, boolean overdue, String href) {
    }

    public record PaymentWatchItem(
            UUID bookingId,
            String bookingNumber,
            BigDecimal paidAmount,
            BigDecimal balanceDue,
            String paymentStatus,
            String href
    ) {
    }

    public record AllocationWarningItem(
            UUID bookingId,
            String bookingNumber,
            LocalDate travelStart,
            String warning,
            String href
    ) {
    }

    public record ActivityItem(UUID id, String title, String body, Instant createdAt) {
    }
}
