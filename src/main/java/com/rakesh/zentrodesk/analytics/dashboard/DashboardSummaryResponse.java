package com.rakesh.zentrodesk.analytics.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardSummaryResponse(
        LocalDate date,
        List<KpiCard> kpis,
        List<TaskQueueItem> taskQueue,
        List<PaymentWatchItem> paymentWatch,
        List<ActivityItem> activityFeed,
        Map<String, Long> leadPipelineCounts
) {

    public record KpiCard(String label, String value, String detail) {
    }

    public record TaskQueueItem(UUID id, String title, String status, Instant dueAt) {
    }

    public record PaymentWatchItem(UUID bookingId, String bookingNumber, BigDecimal paidAmount, String paymentStatus) {
    }

    public record ActivityItem(UUID id, String title, String body, Instant createdAt) {
    }
}
