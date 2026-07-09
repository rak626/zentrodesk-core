package com.rakesh.zentrodesk.analytics.dashboard;

import com.rakesh.zentrodesk.communication.notification.Notification;
import com.rakesh.zentrodesk.communication.notification.NotificationRepository;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.PaymentRepository;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import com.rakesh.zentrodesk.operations.task.Task;
import com.rakesh.zentrodesk.operations.task.TaskRepository;
import com.rakesh.zentrodesk.operations.task.TaskStatus;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<PaymentStatus> PAID_STATUSES = List.of(PaymentStatus.PAID, PaymentStatus.PARTIAL);

    private final CurrentTenant currentTenant;
    private final LeadRepository leadRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RoomRepository roomRepository;
    private final DriverRepository driverRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(LocalDate date) {
        UUID tenantId = currentTenant.id();
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        long activeBookings = bookingRepository.countByTenantIdAndActiveTrue(tenantId);
        long activeLeads = leadRepository.countByTenantIdAndActiveTrue(tenantId);
        long openTasks = taskRepository.countByTenantIdAndStatusAndActiveTrue(tenantId, TaskStatus.PENDING)
                + taskRepository.countByTenantIdAndStatusAndActiveTrue(tenantId, TaskStatus.IN_PROGRESS);
        long availableRooms = roomRepository.countByPropertyTenantIdAndStatusAndActiveTrue(tenantId, RoomStatus.AVAILABLE);
        long availableDrivers = driverRepository.countByTenantIdAndStatusAndActiveTrue(tenantId, DriverStatus.AVAILABLE);

        List<DashboardSummaryResponse.KpiCard> kpis = List.of(
                new DashboardSummaryResponse.KpiCard("Active bookings", String.valueOf(activeBookings), "Tenant scoped"),
                new DashboardSummaryResponse.KpiCard("Open leads", String.valueOf(activeLeads), "Active pipeline"),
                new DashboardSummaryResponse.KpiCard("Tasks due", String.valueOf(openTasks), "Pending or in progress"),
                new DashboardSummaryResponse.KpiCard("Capacity", availableRooms + " rooms", availableDrivers + " drivers available")
        );

        List<Task> tasks = taskRepository.findAll(
                (Specification<Task>) (root, query, cb) -> cb.and(cb.equal(root.get("tenantId"), tenantId), cb.equal(root.get("active"), true)),
                PageRequest.of(0, 6, Sort.by(Sort.Direction.ASC, "dueAt"))
        ).getContent();

        List<Booking> bookings = bookingRepository.findAll(
                (Specification<Booking>) (root, query, cb) -> cb.and(cb.equal(root.get("tenantId"), tenantId), cb.equal(root.get("active"), true)),
                PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        List<Notification> notifications = notificationRepository.findAll(
                PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        Map<String, Long> pipeline = new LinkedHashMap<>();
        Arrays.stream(LeadStatus.values()).forEach(status -> pipeline.put(status.name(), leadRepository.countByTenantIdAndStatusAndActiveTrue(tenantId, status)));

        return new DashboardSummaryResponse(
                effectiveDate,
                kpis,
                tasks.stream().map(task -> new DashboardSummaryResponse.TaskQueueItem(task.getId(), task.getTitle(), task.getStatus().name(), task.getDueAt())).toList(),
                bookings.stream().map(booking -> {
                    BigDecimal paid = paymentRepository.sumActiveByBookingAndStatuses(booking.getId(), PAID_STATUSES);
                    String status = paid.signum() <= 0 ? PaymentStatus.PENDING.name() : PaymentStatus.PARTIAL.name();
                    return new DashboardSummaryResponse.PaymentWatchItem(booking.getId(), booking.getBookingNumber(), paid, status);
                }).toList(),
                notifications.stream().map(notification -> new DashboardSummaryResponse.ActivityItem(notification.getId(), notification.getTitle(), notification.getMessage(), notification.getCreatedAt())).toList(),
                pipeline
        );
    }
}
