package com.rakesh.zentrodesk.analytics.report;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationalReportService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> LEAD_STATUSES = Set.of(
            "NEW", "CONTACTED", "QUALIFIED", "QUOTATION_SENT", "NEGOTIATING", "WON", "LOST", "CANCELLED"
    );
    private static final Set<String> BOOKING_STATUSES = Set.of("CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED");
    private static final Set<String> PAYMENT_STATUSES = Set.of("PENDING", "PARTIAL", "PAID", "FAILED", "REFUNDED");
    private static final Set<String> RESERVATION_STATUSES = Set.of("RESERVED", "CHECKED_IN", "CHECKED_OUT", "CANCELLED");
    private static final Set<String> ASSIGNMENT_STATUSES = Set.of("PENDING", "CONFIRMED", "CANCELLED");

    private final JdbcClient jdbcClient;
    private final CurrentTenant currentTenant;
    private final AuditEventService auditEventService;

    @Value("${app.reports.export-enabled:true}")
    private boolean exportEnabled;

    @Value("${app.reports.max-export-rows:10000}")
    private int maxExportRows;

    @Transactional(readOnly = true)
    public OperationalReportResponse summary(LocalDate from, LocalDate to, String timezone, String status) {
        UUID tenantId = currentTenant.id();
        ReportRange range = range(from, to, timezone);
        return new OperationalReportResponse(
                range.from(),
                range.to(),
                range.zone().getId(),
                Instant.now(),
                List.of(
                        leadConversion(tenantId, range, statusFor(ReportType.LEAD_CONVERSION, status)),
                        bookings(tenantId, range, statusFor(ReportType.BOOKINGS, status)),
                        payments(tenantId, range, statusFor(ReportType.PAYMENTS, status)),
                        occupancy(tenantId, range, statusFor(ReportType.OCCUPANCY, status)),
                        driverUtilization(tenantId, range, statusFor(ReportType.DRIVER_UTILIZATION, status))
                )
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportDetailRow> details(
            ReportType type,
            LocalDate from,
            LocalDate to,
            String timezone,
            String status,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Report page must be non-negative and size must be between 1 and 100");
        }
        UUID tenantId = currentTenant.id();
        ReportRange range = range(from, to, timezone);
        String resolvedStatus = statusFor(type, status);
        QuerySpec query = detailQuery(type);
        long total = count(query, tenantId, range, resolvedStatus);
        List<ReportDetailRow> rows = rows(query, tenantId, range, resolvedStatus, size, (long) page * size);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(
                rows,
                page,
                size,
                total,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }

    public void exportCsv(
            ReportType type,
            LocalDate from,
            LocalDate to,
            String timezone,
            String status,
            UUID tenantId,
            UUID actorId,
            Writer writer
    ) throws IOException {
        if (!exportEnabled) {
            throw new BadRequestException("Report exports are disabled");
        }
        ReportRange range = range(from, to, timezone);
        String resolvedStatus = statusFor(type, status);
        QuerySpec query = detailQuery(type);
        long total = count(query, tenantId, range, resolvedStatus);
        if (total > maxExportRows) {
            throw new BadRequestException("Report export exceeds the configured row limit");
        }
        List<ReportDetailRow> rows = rows(query, tenantId, range, resolvedStatus, maxExportRows, 0);
        writeCsv(rows, writer);
        auditEventService.record(
                tenantId,
                actorId,
                "REPORT",
                null,
                "EXPORT",
                UUID.randomUUID().toString(),
                null,
                type + " " + range.from() + " to " + range.to() + " rows=" + rows.size()
        );
    }

    private OperationalReportResponse.ReportSection leadConversion(UUID tenantId, ReportRange range, String status) {
        List<OperationalReportResponse.ReportRow> rows = jdbcClient.sql("""
                        select lead.status as key, lead.status as label, count(*) as row_count
                        from zentro_desk.leads lead
                        where lead.tenant_id = :tenantId
                          and lead.active = true
                          and lead.created_at >= :rangeStart
                          and lead.created_at < :rangeEnd
                          and (cast(:status as varchar) is null or lead.status = cast(:status as varchar))
                        group by lead.status
                        order by lead.status
                        """)
                .param("tenantId", tenantId)
                .param("rangeStart", range.rangeStart().atOffset(ZoneOffset.UTC))
                .param("rangeEnd", range.rangeEnd().atOffset(ZoneOffset.UTC))
                .param("status", status)
                .query((rs, rowNum) -> new OperationalReportResponse.ReportRow(
                        rs.getString("key"), rs.getString("label"), rs.getLong("row_count"), null, null
                ))
                .list();
        long total = rows.stream().mapToLong(OperationalReportResponse.ReportRow::count).sum();
        long won = rows.stream().filter(row -> "WON".equals(row.key()))
                .mapToLong(OperationalReportResponse.ReportRow::count).sum();
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(won * 100.0 / total).setScale(2, java.math.RoundingMode.HALF_UP);
        return section(ReportType.LEAD_CONVERSION, "Lead conversion", rows, Map.of(
                "total", BigDecimal.valueOf(total),
                "won", BigDecimal.valueOf(won),
                "conversionRate", rate
        ));
    }

    private OperationalReportResponse.ReportSection bookings(UUID tenantId, ReportRange range, String status) {
        List<OperationalReportResponse.ReportRow> rows = jdbcClient.sql("""
                        select booking.status as key, booking.status as label, count(*) as row_count,
                               coalesce(sum(booking.agreed_value), 0) as amount
                        from zentro_desk.bookings booking
                        where booking.tenant_id = :tenantId
                          and booking.active = true
                          and booking.created_at >= :rangeStart
                          and booking.created_at < :rangeEnd
                          and (cast(:status as varchar) is null or booking.status = cast(:status as varchar))
                        group by booking.status
                        order by booking.status
                        """)
                .param("tenantId", tenantId)
                .param("rangeStart", range.rangeStart().atOffset(ZoneOffset.UTC))
                .param("rangeEnd", range.rangeEnd().atOffset(ZoneOffset.UTC))
                .param("status", status)
                .query((rs, rowNum) -> new OperationalReportResponse.ReportRow(
                        rs.getString("key"), rs.getString("label"), rs.getLong("row_count"),
                        rs.getBigDecimal("amount"), null
                ))
                .list();
        return section(ReportType.BOOKINGS, "Bookings", rows, totals(rows));
    }

    private OperationalReportResponse.ReportSection payments(UUID tenantId, ReportRange range, String status) {
        List<OperationalReportResponse.ReportRow> rows = jdbcClient.sql("""
                        select payment.status as key, payment.status as label, count(*) as row_count,
                               coalesce(sum(payment.amount), 0) as amount
                        from zentro_desk.payments payment
                        where payment.tenant_id = :tenantId
                          and payment.active = true
                          and coalesce(payment.paid_at, payment.created_at) >= :rangeStart
                          and coalesce(payment.paid_at, payment.created_at) < :rangeEnd
                          and (cast(:status as varchar) is null or payment.status = cast(:status as varchar))
                        group by payment.status
                        order by payment.status
                        """)
                .param("tenantId", tenantId)
                .param("rangeStart", range.rangeStart().atOffset(ZoneOffset.UTC))
                .param("rangeEnd", range.rangeEnd().atOffset(ZoneOffset.UTC))
                .param("status", status)
                .query((rs, rowNum) -> new OperationalReportResponse.ReportRow(
                        rs.getString("key"), rs.getString("label"), rs.getLong("row_count"),
                        rs.getBigDecimal("amount"), null
                ))
                .list();
        return section(ReportType.PAYMENTS, "Payments", rows, totals(rows));
    }

    private OperationalReportResponse.ReportSection occupancy(UUID tenantId, ReportRange range, String status) {
        List<OperationalReportResponse.ReportRow> rows = jdbcClient.sql("""
                        select property.id::text as key, property.name as label,
                               count(reservation.id) as row_count,
                               coalesce(sum(greatest(0, least(reservation.check_out, :toExclusive)
                                   - greatest(reservation.check_in, :fromDate))), 0) as occupied_nights,
                               count(distinct room.id) * (:rangeDays) as total_nights
                        from zentro_desk.properties property
                        join zentro_desk.rooms room on room.property_id = property.id and room.active = true
                        left join zentro_desk.reservations reservation
                          on reservation.property_id = property.id
                         and reservation.room_id = room.id
                         and reservation.active = true
                         and reservation.check_in < :toExclusive
                         and reservation.check_out > :fromDate
                         and (cast(:status as varchar) is null or reservation.status = cast(:status as varchar))
                        where property.tenant_id = :tenantId
                          and property.active = true
                        group by property.id, property.name
                        order by property.name
                        """)
                .param("tenantId", tenantId)
                .param("fromDate", range.from())
                .param("toExclusive", range.to().plusDays(1))
                .param("rangeDays", ChronoUnit.DAYS.between(range.from(), range.to()) + 1)
                .param("status", status)
                .query((rs, rowNum) -> {
                    BigDecimal occupied = rs.getBigDecimal("occupied_nights");
                    BigDecimal total = rs.getBigDecimal("total_nights");
                    BigDecimal rate = total.signum() == 0 ? BigDecimal.ZERO
                            : occupied.multiply(BigDecimal.valueOf(100))
                            .divide(total, 2, java.math.RoundingMode.HALF_UP);
                    return new OperationalReportResponse.ReportRow(
                            rs.getString("key"), rs.getString("label"), rs.getLong("row_count"),
                            occupied, rate
                    );
                })
                .list();
        BigDecimal occupied = rows.stream().map(OperationalReportResponse.ReportRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return section(ReportType.OCCUPANCY, "Occupancy", rows, Map.of("occupiedRoomNights", occupied));
    }

    private OperationalReportResponse.ReportSection driverUtilization(UUID tenantId, ReportRange range, String status) {
        List<OperationalReportResponse.ReportRow> rows = jdbcClient.sql("""
                        select driver.id::text as key, driver.name as label, count(assignment.id) as row_count,
                               coalesce(sum(extract(epoch from (
                                   least(assignment.dropoff_at, :rangeEnd)
                                   - greatest(assignment.pickup_at, :rangeStart)
                               )) / 3600), 0) as assigned_hours
                        from zentro_desk.drivers driver
                        left join zentro_desk.driver_assignments assignment
                          on assignment.driver_id = driver.id
                         and assignment.tenant_id = driver.tenant_id
                         and assignment.active = true
                         and assignment.pickup_at < :rangeEnd
                         and assignment.dropoff_at > :rangeStart
                         and (cast(:status as varchar) is null or assignment.status = cast(:status as varchar))
                        where driver.tenant_id = :tenantId
                          and driver.active = true
                        group by driver.id, driver.name
                        order by driver.name
                        """)
                .param("tenantId", tenantId)
                .param("rangeStart", range.rangeStart().atOffset(ZoneOffset.UTC))
                .param("rangeEnd", range.rangeEnd().atOffset(ZoneOffset.UTC))
                .param("status", status)
                .query((rs, rowNum) -> new OperationalReportResponse.ReportRow(
                        rs.getString("key"), rs.getString("label"), rs.getLong("row_count"),
                        rs.getBigDecimal("assigned_hours"), null
                ))
                .list();
        BigDecimal hours = rows.stream().map(OperationalReportResponse.ReportRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return section(ReportType.DRIVER_UTILIZATION, "Driver utilization", rows, Map.of("assignedHours", hours));
    }

    private OperationalReportResponse.ReportSection section(
            ReportType type,
            String label,
            List<OperationalReportResponse.ReportRow> rows,
            Map<String, BigDecimal> metrics
    ) {
        return new OperationalReportResponse.ReportSection(type, label, metrics, rows);
    }

    private Map<String, BigDecimal> totals(List<OperationalReportResponse.ReportRow> rows) {
        BigDecimal count = BigDecimal.valueOf(rows.stream().mapToLong(OperationalReportResponse.ReportRow::count).sum());
        BigDecimal amount = rows.stream().map(OperationalReportResponse.ReportRow::amount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("total", count, "amount", amount);
    }

    private QuerySpec detailQuery(ReportType type) {
        return switch (type) {
            case LEAD_CONVERSION -> new QuerySpec(
                    "lead.id, lead.name, lead.status, lead.source, lead.created_at",
                    "zentro_desk.leads lead",
                    "lead.tenant_id = :tenantId and lead.active = true and lead.created_at >= :rangeStart and lead.created_at < :rangeEnd",
                    "lead.created_at desc",
                    "lead.status"
            );
            case BOOKINGS -> new QuerySpec(
                    "booking.id, booking.booking_number, booking.status, booking.travel_start, booking.travel_end, booking.agreed_value, booking.created_at",
                    "zentro_desk.bookings booking",
                    "booking.tenant_id = :tenantId and booking.active = true and booking.created_at >= :rangeStart and booking.created_at < :rangeEnd",
                    "booking.created_at desc",
                    "booking.status"
            );
            case PAYMENTS -> new QuerySpec(
                    "payment.id, payment.booking_id, payment.status, payment.amount, payment.method, payment.paid_at, payment.created_at",
                    "zentro_desk.payments payment",
                    "payment.tenant_id = :tenantId and payment.active = true and coalesce(payment.paid_at, payment.created_at) >= :rangeStart and coalesce(payment.paid_at, payment.created_at) < :rangeEnd",
                    "coalesce(payment.paid_at, payment.created_at) desc",
                    "payment.status"
            );
            case OCCUPANCY -> new QuerySpec(
                    "reservation.id, property.name as property, room.name as room, reservation.status, reservation.check_in, reservation.check_out",
                    "zentro_desk.reservations reservation join zentro_desk.properties property on property.id = reservation.property_id join zentro_desk.rooms room on room.id = reservation.room_id",
                    "property.tenant_id = :tenantId and reservation.active = true and reservation.check_in < :toExclusive and reservation.check_out > :fromDate",
                    "reservation.check_in desc",
                    "reservation.status"
            );
            case DRIVER_UTILIZATION -> new QuerySpec(
                    "assignment.id, driver.name as driver, vehicle.registration_number as vehicle, assignment.status, assignment.pickup_at, assignment.dropoff_at",
                    "zentro_desk.driver_assignments assignment join zentro_desk.drivers driver on driver.id = assignment.driver_id join zentro_desk.vehicles vehicle on vehicle.id = assignment.vehicle_id",
                    "assignment.tenant_id = :tenantId and assignment.active = true and assignment.pickup_at < :rangeEnd and assignment.dropoff_at > :rangeStart",
                    "assignment.pickup_at desc",
                    "assignment.status"
            );
        };
    }

    private long count(QuerySpec query, UUID tenantId, ReportRange range, String status) {
        return statement("select count(*) from " + query.from() + " where " + query.where()
                        + " and (cast(:status as varchar) is null or " + query.statusColumn() + " = cast(:status as varchar))",
                tenantId, range, status)
                .query(Long.class)
                .single();
    }

    private List<ReportDetailRow> rows(
            QuerySpec query,
            UUID tenantId,
            ReportRange range,
            String status,
            int limit,
            long offset
    ) {
        String sql = "select " + query.select() + " from " + query.from() + " where " + query.where()
                + " and (cast(:status as varchar) is null or " + query.statusColumn() + " = cast(:status as varchar))"
                + " order by " + query.orderBy() + " limit :limit offset :offset";
        return statement(sql, tenantId, range, status)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    ResultSetMetaData metadata = rs.getMetaData();
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        Object value = rs.getObject(index);
                        values.put(metadata.getColumnLabel(index), value == null ? "" : String.valueOf(value));
                    }
                    return new ReportDetailRow(values);
                })
                .list();
    }

    private JdbcClient.StatementSpec statement(String sql, UUID tenantId, ReportRange range, String status) {
        return jdbcClient.sql(sql)
                .param("tenantId", tenantId)
                .param("rangeStart", range.rangeStart().atOffset(ZoneOffset.UTC))
                .param("rangeEnd", range.rangeEnd().atOffset(ZoneOffset.UTC))
                .param("fromDate", range.from())
                .param("toExclusive", range.to().plusDays(1))
                .param("status", status);
    }

    private void writeCsv(List<ReportDetailRow> rows, Writer writer) throws IOException {
        if (rows.isEmpty()) {
            writer.write("no_data\n");
            return;
        }
        List<String> headers = new ArrayList<>(rows.getFirst().values().keySet());
        writer.write(String.join(",", headers.stream().map(this::csvCell).toList()));
        writer.write("\n");
        for (ReportDetailRow row : rows) {
            writer.write(String.join(",", headers.stream()
                    .map(header -> csvCell(row.values().getOrDefault(header, "")))
                    .toList()));
            writer.write("\n");
        }
        writer.flush();
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private ReportRange range(LocalDate requestedFrom, LocalDate requestedTo, String timezone) {
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? ZoneId.of("UTC") : ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            throw new BadRequestException("Invalid report timezone");
        }
        LocalDate to = requestedTo == null ? LocalDate.now(zone) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) {
            throw new BadRequestException("Report start date must not be after end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BadRequestException("Report range cannot exceed 366 days");
        }
        return new ReportRange(
                from,
                to,
                zone,
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant()
        );
    }

    private String statusFor(ReportType type, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return null;
        }
        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = switch (type) {
            case LEAD_CONVERSION -> LEAD_STATUSES;
            case BOOKINGS -> BOOKING_STATUSES;
            case PAYMENTS -> PAYMENT_STATUSES;
            case OCCUPANCY -> RESERVATION_STATUSES;
            case DRIVER_UTILIZATION -> ASSIGNMENT_STATUSES;
        };
        if (!allowed.contains(status)) {
            return null;
        }
        return status;
    }

    private record QuerySpec(String select, String from, String where, String orderBy, String statusColumn) {
    }
}
