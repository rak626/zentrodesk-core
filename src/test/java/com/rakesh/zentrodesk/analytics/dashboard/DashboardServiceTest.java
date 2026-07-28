package com.rakesh.zentrodesk.analytics.dashboard;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.communication.notification.NotificationRepository;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.PaymentRepository;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.operations.task.TaskRepository;
import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final CurrentTenant currentTenant = mock(CurrentTenant.class);
    private final LeadRepository leadRepository = mock(LeadRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final DashboardService service = new DashboardService(
            currentTenant,
            leadRepository,
            bookingRepository,
            paymentRepository,
            roomRepository,
            driverRepository,
            taskRepository,
            notificationRepository
    );

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRepositories() {
        when(currentTenant.id()).thenReturn(tenantId);
        when(leadRepository.count(any(Specification.class))).thenReturn(0L);
        when(bookingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(bookingRepository.count(any(Specification.class))).thenReturn(0L);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(taskRepository.count(any(Specification.class))).thenReturn(0L);
        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(roomRepository.countByPropertyTenantIdAndStatusAndActiveTrue(any(), any())).thenReturn(0L);
        when(driverRepository.countByTenantIdAndStatusAndActiveTrue(any(), any())).thenReturn(0L);
    }

    @Test
    void fixedRolesReceiveOnlyTheirAuthorizedWidgetsAndActions() {
        Map<FixedTenantRole, List<String>> expectedWidgets = new EnumMap<>(FixedTenantRole.class);
        expectedWidgets.put(FixedTenantRole.OWNER, List.of(
                "SALES_PIPELINE", "TASK_QUEUE", "PAYMENT_WATCH", "ALLOCATION_WARNINGS", "ACTIVITY", "CAPACITY"
        ));
        expectedWidgets.put(FixedTenantRole.MANAGER, expectedWidgets.get(FixedTenantRole.OWNER));
        expectedWidgets.put(FixedTenantRole.SALES, List.of(
                "SALES_PIPELINE", "TASK_QUEUE", "PAYMENT_WATCH", "ACTIVITY"
        ));
        expectedWidgets.put(FixedTenantRole.OPERATIONS, List.of(
                "TASK_QUEUE", "ALLOCATION_WARNINGS", "ACTIVITY", "CAPACITY"
        ));
        expectedWidgets.put(FixedTenantRole.ACCOUNTS, List.of("PAYMENT_WATCH", "ACTIVITY"));

        Map<FixedTenantRole, List<String>> expectedActions = new EnumMap<>(FixedTenantRole.class);
        expectedActions.put(FixedTenantRole.OWNER, List.of(
                "LEAD_CREATE", "BOOKING_CREATE", "PAYMENT_CREATE", "RESERVATION_CREATE", "DRIVER_UPDATE"
        ));
        expectedActions.put(FixedTenantRole.MANAGER, expectedActions.get(FixedTenantRole.OWNER));
        expectedActions.put(FixedTenantRole.SALES, List.of("LEAD_CREATE", "BOOKING_CREATE"));
        expectedActions.put(FixedTenantRole.OPERATIONS, List.of("RESERVATION_CREATE", "DRIVER_UPDATE"));
        expectedActions.put(FixedTenantRole.ACCOUNTS, List.of("PAYMENT_CREATE"));

        for (FixedTenantRole role : FixedTenantRole.values()) {
            DashboardSummaryResponse response = service.summary(
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 30),
                    "Asia/Kolkata",
                    principal(role)
            );

            assertEquals(expectedWidgets.get(role), response.visibleWidgets(), role.name());
            assertEquals(expectedActions.get(role), response.actions().stream()
                    .map(DashboardSummaryResponse.ActionLink::permission)
                    .toList(), role.name());
            assertEquals("Asia/Kolkata", response.timezone(), role.name());
        }
    }

    @Test
    void rejectsInvalidTimezoneAndDateRanges() {
        TenantPrincipal owner = principal(FixedTenantRole.OWNER);

        assertThrows(BadRequestException.class, () -> service.summary(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 30),
                "Not/A-Timezone",
                owner
        ));
        assertThrows(BadRequestException.class, () -> service.summary(
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 7, 1),
                "UTC",
                owner
        ));
        assertThrows(BadRequestException.class, () -> service.summary(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 7, 30),
                "UTC",
                owner
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsOneWidgetFailureWithoutBlankingAuthorizedPeers() {
        when(leadRepository.count(any(Specification.class)))
                .thenThrow(new IllegalStateException("lead database unavailable"));

        DashboardSummaryResponse response = service.summary(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 30),
                "UTC",
                principal(FixedTenantRole.SALES)
        );

        assertEquals(
                "Lead pipeline is temporarily unavailable",
                response.widgetErrors().get("SALES_PIPELINE")
        );
        assertTrue(response.visibleWidgets().contains("TASK_QUEUE"));
        assertTrue(response.kpis().stream().anyMatch(kpi -> "Open tasks".equals(kpi.label())));
    }

    private TenantPrincipal principal(FixedTenantRole role) {
        List<SimpleGrantedAuthority> authorities = role.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new TenantPrincipal(
                UUID.randomUUID(),
                tenantId,
                role.name().toLowerCase() + "@example.test",
                "",
                role.displayName(),
                null,
                UUID.randomUUID(),
                role.displayName(),
                true,
                0,
                authorities
        );
    }
}
