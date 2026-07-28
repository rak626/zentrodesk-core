package com.rakesh.zentrodesk.analytics.dashboard;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.communication.notification.Notification;
import com.rakesh.zentrodesk.communication.notification.NotificationRepository;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.booking.PaymentRepository;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import com.rakesh.zentrodesk.operations.task.Task;
import com.rakesh.zentrodesk.operations.task.TaskRepository;
import com.rakesh.zentrodesk.operations.task.TaskStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<PaymentStatus> PAID_STATUSES = List.of(PaymentStatus.PAID, PaymentStatus.PARTIAL);
    private static final int QUEUE_LIMIT = 8;

    private final CurrentTenant currentTenant;
    private final LeadRepository leadRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RoomRepository roomRepository;
    private final DriverRepository driverRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;

    public DashboardSummaryResponse summary(LocalDate date) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CurrentUser user)) {
            throw new com.rakesh.zentrodesk.common.exception.UnauthorizedException(
                    "Tenant authentication required"
            );
        }
        return summary(date, date, "UTC", user, true);
    }

    public DashboardSummaryResponse summary(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String timezone,
            CurrentUser user
    ) {
        return summary(requestedFrom, requestedTo, timezone, user, false);
    }

    private DashboardSummaryResponse summary(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String timezone,
            CurrentUser user,
            boolean includeLegacyActivity
    ) {
        UUID tenantId = currentTenant.id();
        ZoneId zone = zone(timezone);
        LocalDate to = requestedTo == null ? LocalDate.now(zone) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        validateRange(from, to);
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();
        boolean elevated = "Owner".equalsIgnoreCase(user.roleName()) || "Manager".equalsIgnoreCase(user.roleName());

        List<String> widgets = visibleWidgets(user, includeLegacyActivity);
        List<DashboardSummaryResponse.ActionLink> actions = actions(user);
        Map<String, String> errors = new LinkedHashMap<>();
        List<DashboardSummaryResponse.KpiCard> kpis = new java.util.ArrayList<>();

        Specification<Lead> leadScope = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.isTrue(root.get("active")),
                elevated ? cb.conjunction() : cb.equal(root.get("assignedUserId"), user.id()),
                cb.greaterThanOrEqualTo(root.get("createdAt"), rangeStart),
                cb.lessThan(root.get("createdAt"), rangeEnd)
        );
        Map<String, Long> pipeline = new LinkedHashMap<>();
        if (widgets.contains("SALES_PIPELINE")) {
            try {
                Arrays.stream(LeadStatus.values()).forEach(status -> pipeline.put(
                        status.name(),
                        leadRepository.count(leadScope.and((root, query, cb) -> cb.equal(root.get("status"), status)))
                ));
                kpis.add(new DashboardSummaryResponse.KpiCard(
                        "Open leads",
                        String.valueOf(pipeline.entrySet().stream()
                                .filter(entry -> entry.getKey().matches("NEW|CONTACTED|QUALIFIED|QUOTATION_SENT|NEGOTIATING"))
                                .mapToLong(Map.Entry::getValue)
                                .sum()),
                        elevated ? "Tenant pipeline" : "Your assigned pipeline"
                ));
            } catch (RuntimeException exception) {
                errors.put("SALES_PIPELINE", "Lead pipeline is temporarily unavailable");
            }
        }

        Specification<Booking> bookingScope = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.isTrue(root.get("active")),
                cb.or(
                        cb.between(root.get("travelStart"), from, to),
                        cb.and(cb.isNull(root.get("travelStart")), cb.greaterThanOrEqualTo(root.get("createdAt"), rangeStart),
                                cb.lessThan(root.get("createdAt"), rangeEnd))
                )
        );
        List<Booking> bookings = List.of();
        if (has(user, TenantPermissions.BOOKING_READ)) {
            try {
                bookings = bookingRepository.findAll(
                        bookingScope,
                        PageRequest.of(0, QUEUE_LIMIT, Sort.by(Sort.Direction.ASC, "travelStart"))
                ).getContent();
                kpis.add(new DashboardSummaryResponse.KpiCard(
                        "Bookings",
                        String.valueOf(bookingRepository.count(bookingScope)),
                        from + " to " + to
                ));
            } catch (RuntimeException exception) {
                errors.put("BOOKINGS", "Booking summary is temporarily unavailable");
            }
        }

        List<DashboardSummaryResponse.TaskQueueItem> taskQueue = List.of();
        if (widgets.contains("TASK_QUEUE")) {
            try {
                Specification<Task> taskScope = (root, query, cb) -> cb.and(
                        cb.equal(root.get("tenantId"), tenantId),
                        cb.isTrue(root.get("active")),
                        root.get("status").in(TaskStatus.PENDING, TaskStatus.IN_PROGRESS),
                        elevated ? cb.conjunction() : cb.equal(root.get("userId"), user.id())
                );
                List<Task> tasks = taskRepository.findAll(
                        taskScope,
                        PageRequest.of(0, QUEUE_LIMIT, Sort.by(Sort.Direction.ASC, "dueAt"))
                ).getContent();
                taskQueue = tasks.stream().map(task -> new DashboardSummaryResponse.TaskQueueItem(
                        task.getId(),
                        task.getTitle(),
                        task.getStatus().name(),
                        task.getDueAt(),
                        task.getDueAt() != null && task.getDueAt().isBefore(now),
                        task.getSourceType() != null && "LEAD_FOLLOW_UP".equals(task.getSourceType())
                                ? "/leads?followUp=" + task.getSourceId()
                                : "/dashboard#tasks"
                )).toList();
                kpis.add(new DashboardSummaryResponse.KpiCard(
                        "Open tasks",
                        String.valueOf(taskRepository.count(taskScope)),
                        elevated ? "Tenant queue" : "Your queue"
                ));
            } catch (RuntimeException exception) {
                errors.put("TASK_QUEUE", "Task queue is temporarily unavailable");
            }
        }

        List<DashboardSummaryResponse.PaymentWatchItem> paymentWatch = List.of();
        if (widgets.contains("PAYMENT_WATCH") && !bookings.isEmpty()) {
            try {
                paymentWatch = bookings.stream().map(booking -> {
                    BigDecimal paid = paymentRepository.sumActiveByBookingAndStatuses(booking.getId(), PAID_STATUSES);
                    BigDecimal agreed = booking.getAgreedValue() == null ? BigDecimal.ZERO : booking.getAgreedValue();
                    BigDecimal balance = agreed.subtract(paid).max(BigDecimal.ZERO);
                    String status = paid.signum() == 0
                            ? PaymentStatus.PENDING.name()
                            : balance.signum() == 0 ? PaymentStatus.PAID.name() : PaymentStatus.PARTIAL.name();
                    return new DashboardSummaryResponse.PaymentWatchItem(
                            booking.getId(),
                            booking.getBookingNumber(),
                            paid,
                            balance,
                            status,
                            "/bookings?booking=" + booking.getId()
                    );
                }).filter(item -> item.balanceDue().signum() > 0).toList();
            } catch (RuntimeException exception) {
                errors.put("PAYMENT_WATCH", "Payment watch is temporarily unavailable");
            }
        }

        List<DashboardSummaryResponse.AllocationWarningItem> allocationWarnings = List.of();
        if (widgets.contains("ALLOCATION_WARNINGS") && !bookings.isEmpty()) {
            allocationWarnings = bookings.stream()
                    .filter(booking -> booking.getDriver() == null)
                    .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED
                            || booking.getStatus() == BookingStatus.IN_PROGRESS)
                    .map(booking -> new DashboardSummaryResponse.AllocationWarningItem(
                            booking.getId(),
                            booking.getBookingNumber(),
                            booking.getTravelStart(),
                            "Driver not allocated",
                            "/drivers?booking=" + booking.getId()
                    ))
                    .toList();
        }

        List<DashboardSummaryResponse.ActivityItem> activity = List.of();
        if (widgets.contains("ACTIVITY")) {
            try {
                List<Notification> notifications = notificationRepository.findAll(
                        (Specification<Notification>) (root, query, cb) -> cb.and(
                                cb.equal(root.get("tenantId"), tenantId),
                                cb.equal(root.get("userId"), user.id()),
                                cb.isTrue(root.get("active"))
                        ),
                        PageRequest.of(0, QUEUE_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).getContent();
                activity = notifications.stream().map(notification -> new DashboardSummaryResponse.ActivityItem(
                        notification.getId(),
                        notification.getTitle(),
                        notification.getMessage(),
                        notification.getCreatedAt()
                )).toList();
            } catch (RuntimeException exception) {
                errors.put("ACTIVITY", "Recent activity is temporarily unavailable");
            }
        }

        if (widgets.contains("CAPACITY")) {
            try {
                long rooms = roomRepository.countByPropertyTenantIdAndStatusAndActiveTrue(
                        tenantId, RoomStatus.AVAILABLE
                );
                long drivers = driverRepository.countByTenantIdAndStatusAndActiveTrue(
                        tenantId, DriverStatus.AVAILABLE
                );
                kpis.add(new DashboardSummaryResponse.KpiCard(
                        "Capacity",
                        rooms + " rooms",
                        drivers + " drivers available"
                ));
            } catch (RuntimeException exception) {
                errors.put("CAPACITY", "Capacity is temporarily unavailable");
            }
        }

        return new DashboardSummaryResponse(
                to,
                zone.getId(),
                user.roleName(),
                now,
                widgets,
                actions,
                kpis,
                taskQueue,
                paymentWatch,
                allocationWarnings,
                activity,
                pipeline,
                errors
        );
    }

    private List<String> visibleWidgets(CurrentUser user, boolean includeLegacyActivity) {
        List<String> widgets = new java.util.ArrayList<>();
        if (has(user, TenantPermissions.LEAD_READ)) widgets.add("SALES_PIPELINE");
        if (has(user, TenantPermissions.TASK_READ)) widgets.add("TASK_QUEUE");
        if (has(user, TenantPermissions.PAYMENT_READ)) widgets.add("PAYMENT_WATCH");
        if (has(user, TenantPermissions.BOOKING_READ) && has(user, TenantPermissions.DRIVER_READ)) {
            widgets.add("ALLOCATION_WARNINGS");
        }
        if (includeLegacyActivity || has(user, TenantPermissions.NOTIFICATION_READ)) widgets.add("ACTIVITY");
        if (has(user, TenantPermissions.ROOM_READ) || has(user, TenantPermissions.DRIVER_READ)) widgets.add("CAPACITY");
        return List.copyOf(widgets);
    }

    private List<DashboardSummaryResponse.ActionLink> actions(CurrentUser user) {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put(TenantPermissions.LEAD_CREATE, "/leads?action=create");
        candidates.put(TenantPermissions.BOOKING_CREATE, "/bookings?action=create");
        candidates.put(TenantPermissions.PAYMENT_CREATE, "/bookings?action=payment");
        candidates.put(TenantPermissions.RESERVATION_CREATE, "/availability?action=reserve");
        candidates.put(TenantPermissions.DRIVER_UPDATE, "/drivers?action=assign");
        return candidates.entrySet().stream()
                .filter(entry -> has(user, entry.getKey()))
                .map(entry -> new DashboardSummaryResponse.ActionLink(
                        entry.getKey().replace("_CREATE", "").replace("_UPDATE", "").replace("_", " "),
                        entry.getValue(),
                        entry.getKey()
                ))
                .toList();
    }

    private boolean has(CurrentUser user, String authority) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            throw new BadRequestException("Invalid timezone");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("Dashboard start date must not be after end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BadRequestException("Dashboard range cannot exceed 366 days");
        }
    }
}
