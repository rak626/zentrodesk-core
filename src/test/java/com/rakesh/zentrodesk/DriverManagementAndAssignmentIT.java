package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;
import com.rakesh.zentrodesk.operations.assignment.api.AssignmentReassignRequest;
import com.rakesh.zentrodesk.operations.assignment.api.AssignmentRequest;
import com.rakesh.zentrodesk.operations.assignment.api.AssignmentResponse;
import com.rakesh.zentrodesk.operations.assignment.api.AssignmentService;
import com.rakesh.zentrodesk.operations.assignment.api.AssignmentTransitionRequest;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.booking.api.BookingRequest;
import com.rakesh.zentrodesk.operations.booking.api.BookingResponse;
import com.rakesh.zentrodesk.operations.booking.api.BookingService;
import com.rakesh.zentrodesk.operations.booking.api.BookingStatusRequest;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import com.rakesh.zentrodesk.operations.driver.VehicleStatus;
import com.rakesh.zentrodesk.operations.driver.api.DriverRequest;
import com.rakesh.zentrodesk.operations.driver.api.DriverResponse;
import com.rakesh.zentrodesk.operations.driver.api.DriverService;
import com.rakesh.zentrodesk.operations.driver.api.DriverStatusRequest;
import com.rakesh.zentrodesk.operations.driver.api.VehicleRequest;
import com.rakesh.zentrodesk.operations.driver.api.VehicleResponse;
import com.rakesh.zentrodesk.operations.driver.api.VehicleService;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.sales.customer.api.CustomerRequest;
import com.rakesh.zentrodesk.sales.customer.api.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverManagementAndAssignmentIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private AssignmentService assignmentService;

    @AfterEach
    void clearAuthentication() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void managesSensitiveDriverDataVehiclesAndImmutableStatusHistory() {
        Workspace workspace = createWorkspace("driver-management");
        authenticate(workspace, true);
        VehicleResponse vehicle = createVehicle("SK-01-A-1001");
        DriverResponse driver = createDriver("Asha Rai", vehicle.id(), "9000000001", "DL-1001");

        assertEquals(vehicle.id(), driver.vehicleId());
        assertEquals("9000000001", driver.phone());
        assertEquals(1, driverService.history(driver.id(), 0, 20).totalElements());

        DriverResponse unavailable = driverService.updateStatus(
                driver.id(),
                new DriverStatusRequest(DriverStatus.UNAVAILABLE, "Annual leave", driver.version())
        );
        assertEquals(DriverStatus.UNAVAILABLE, unavailable.status());
        assertEquals(2, driverService.history(driver.id(), 0, 20).totalElements());
        UUID historyId = driverService.history(driver.id(), 0, 20).content().getFirst().id();
        assertThrows(DataAccessException.class, () -> jdbcClient.sql("""
                        update zentro_desk.driver_status_history
                        set reason = 'tampered'
                        where id = :id
                        """)
                .param("id", historyId)
                .update());

        authenticate(workspace, false);
        DriverResponse masked = driverService.findById(driver.id());
        assertNull(masked.phone());
        assertNull(masked.licenseNumber());
        assertFalse(masked.sensitiveFieldsVisible());

        Workspace otherTenant = createWorkspace("driver-isolation");
        authenticate(otherTenant, true);
        assertThrows(ResourceNotFoundException.class, () -> driverService.findById(driver.id()));
        assertThrows(ResourceNotFoundException.class, () -> vehicleService.findById(vehicle.id()));
    }

    @Test
    void assignsReassignsConfirmsAndCancelsWithoutTimeOverlaps() {
        Workspace workspace = createWorkspace("assignments");
        authenticate(workspace, true);
        BookingResponse firstBooking = createBooking("first");
        BookingResponse secondBooking = createBooking("second");
        VehicleResponse firstVehicle = createVehicle("SK-01-A-2001");
        VehicleResponse secondVehicle = createVehicle("SK-01-A-2002");
        DriverResponse firstDriver = createDriver(
                "Pema Dorjee",
                firstVehicle.id(),
                "9000000002",
                "DL-2001"
        );
        DriverResponse secondDriver = createDriver(
                "Nima Rai",
                secondVehicle.id(),
                "9000000003",
                "DL-2002"
        );
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant dropoff = pickup.plus(6, ChronoUnit.HOURS);

        AssignmentResponse assignment = assignmentService.assign(new AssignmentRequest(
                firstBooking.id(),
                firstDriver.id(),
                firstVehicle.id(),
                pickup,
                dropoff,
                "Gangtok hotel",
                "Bagdogra airport",
                "assignment-first"
        ));
        AssignmentResponse replay = assignmentService.assign(new AssignmentRequest(
                firstBooking.id(),
                firstDriver.id(),
                firstVehicle.id(),
                pickup,
                dropoff,
                "Gangtok hotel",
                "Bagdogra airport",
                "assignment-first"
        ));
        assertEquals(assignment.id(), replay.id());
        assertFalse(assignmentService.availability(pickup, dropoff).drivers().stream()
                .filter(driver -> driver.id().equals(firstDriver.id()))
                .findFirst()
                .orElseThrow()
                .available());

        assertThrows(ConflictException.class, () -> assignmentService.assign(new AssignmentRequest(
                secondBooking.id(),
                firstDriver.id(),
                firstVehicle.id(),
                pickup.plus(1, ChronoUnit.HOURS),
                dropoff.plus(1, ChronoUnit.HOURS),
                "Airport",
                "Hotel",
                "assignment-overlap"
        )));

        AssignmentResponse adjacent = assignmentService.assign(new AssignmentRequest(
                secondBooking.id(),
                firstDriver.id(),
                firstVehicle.id(),
                dropoff,
                dropoff.plus(2, ChronoUnit.HOURS),
                "Airport",
                "Hotel",
                "assignment-adjacent"
        ));
        assertEquals(AssignmentStatus.PENDING, adjacent.status());

        AssignmentResponse confirmed = assignmentService.confirm(
                assignment.id(),
                new AssignmentTransitionRequest(assignment.version(), "Customer approved")
        );
        assertEquals(AssignmentStatus.CONFIRMED, confirmed.status());
        assertEquals(DriverStatus.ON_TRIP, driverService.findById(firstDriver.id()).status());
        assertThrows(ConflictException.class, () -> driverService.delete(firstDriver.id()));

        AssignmentResponse reassigned = assignmentService.reassign(
                confirmed.id(),
                new AssignmentReassignRequest(
                        secondDriver.id(),
                        secondVehicle.id(),
                        confirmed.version(),
                        "Driver substitution"
                )
        );
        assertEquals(secondDriver.id(), reassigned.driverId());
        assertEquals(DriverStatus.ON_TRIP, driverService.findById(secondDriver.id()).status());

        AssignmentResponse cancelled = assignmentService.cancel(
                reassigned.id(),
                new AssignmentTransitionRequest(reassigned.version(), "Trip cancelled")
        );
        AssignmentResponse cancellationReplay = assignmentService.cancel(
                reassigned.id(),
                new AssignmentTransitionRequest(reassigned.version(), "Trip cancelled")
        );
        assertEquals(AssignmentStatus.CANCELLED, cancellationReplay.status());
        assertFalse(cancelled.active());
        assertEquals(4, assignmentService.history(cancelled.id(), 0, 20).totalElements());
        assertEquals(8L, notificationCount(cancelled.id()));

        BookingResponse currentSecondBooking = bookingService.findById(secondBooking.id());
        bookingService.updateStatus(
                currentSecondBooking.id(),
                new BookingStatusRequest(BookingStatus.CANCELLED, currentSecondBooking.version())
        );
        assertEquals(AssignmentStatus.CANCELLED, assignmentService.findById(adjacent.id()).status());
    }

    @Test
    void databaseSerializesConcurrentDriverAndVehicleOverlaps() throws Exception {
        Workspace workspace = createWorkspace("assignment-concurrency");
        authenticate(workspace, true);
        BookingResponse firstBooking = createBooking("concurrent-first");
        BookingResponse secondBooking = createBooking("concurrent-second");
        VehicleResponse vehicle = createVehicle("SK-01-A-3001");
        DriverResponse driver = createDriver(
                "Karma Lepcha",
                vehicle.id(),
                "9000000004",
                "DL-3001"
        );
        Instant pickup = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant dropoff = pickup.plus(4, ChronoUnit.HOURS);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            insertAssignment(
                    first,
                    workspace.tenantId(),
                    firstBooking.id(),
                    driver.id(),
                    vehicle.id(),
                    pickup,
                    dropoff,
                    "concurrent-first"
            );

            Future<String> competingInsert = executor.submit(() -> {
                started.countDown();
                try {
                    insertAssignment(
                            second,
                            workspace.tenantId(),
                            secondBooking.id(),
                            driver.id(),
                            vehicle.id(),
                            pickup.plus(1, ChronoUnit.HOURS),
                            dropoff.plus(1, ChronoUnit.HOURS),
                            "concurrent-second"
                    );
                    second.commit();
                    return "inserted";
                } catch (SQLException exception) {
                    second.rollback();
                    return exception.getSQLState();
                }
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> competingInsert.get(200, TimeUnit.MILLISECONDS));
            first.commit();
            assertEquals("23P01", competingInsert.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private BookingResponse createBooking(String suffix) {
        UUID customerId = customerService.create(new CustomerRequest(
                "Traveller " + suffix,
                "91" + Math.abs(suffix.hashCode()) + "00",
                "traveller-" + suffix + "-" + UUID.randomUUID() + "@example.com",
                null,
                null,
                null,
                null,
                null,
                true,
                null
        )).id();
        return bookingService.create(new BookingRequest(
                customerId,
                null,
                null,
                null,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(10),
                null,
                null,
                true,
                new BigDecimal("5000.00"),
                "booking-" + UUID.randomUUID(),
                null
        ));
    }

    private VehicleResponse createVehicle(String registration) {
        return vehicleService.create(new VehicleRequest(
                registration,
                "SUV",
                "Mahindra",
                "Scorpio",
                7,
                VehicleStatus.AVAILABLE,
                true,
                null
        ));
    }

    private DriverResponse createDriver(
            String name,
            UUID vehicleId,
            String phone,
            String licenseNumber
    ) {
        return driverService.create(new DriverRequest(
                name,
                phone,
                licenseNumber,
                vehicleId,
                null,
                null,
                DriverStatus.AVAILABLE,
                true,
                null
        ));
    }

    private long notificationCount(UUID assignmentId) {
        return jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.assignment_notification_outbox
                        where assignment_id = :assignmentId
                        """)
                .param("assignmentId", assignmentId)
                .query(Long.class)
                .single();
    }

    private void insertAssignment(
            Connection connection,
            UUID tenantId,
            UUID bookingId,
            UUID driverId,
            UUID vehicleId,
            Instant pickupAt,
            Instant dropoffAt,
            String idempotencyKey
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into zentro_desk.driver_assignments
                    (id, created_at, updated_at, tenant_id, booking_id, driver_id, vehicle_id,
                     pickup_at, dropoff_at, pickup_location, dropoff_location, status,
                     idempotency_key, active, version)
                values (?, current_timestamp, current_timestamp, ?, ?, ?, ?, ?, ?,
                        'Pickup', 'Drop-off', 'PENDING', ?, true, 0)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenantId);
            statement.setObject(3, bookingId);
            statement.setObject(4, driverId);
            statement.setObject(5, vehicleId);
            statement.setTimestamp(6, Timestamp.from(pickupAt));
            statement.setTimestamp(7, Timestamp.from(dropoffAt));
            statement.setString(8, idempotencyKey);
            statement.executeUpdate();
        }
    }

    private Workspace createWorkspace(String suffix) {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, :name, :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("name", "Tenant " + suffix)
                .param("code", suffix + "-" + tenantId.toString().substring(0, 8))
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_roles
                            (id, created_at, updated_at, tenant_id, name, description, active, template_code)
                        values (:id, current_timestamp, current_timestamp, :tenantId,
                                'Operations', 'Operations', true, 'OPERATIONS')
                        """)
                .param("id", roleId)
                .param("tenantId", tenantId)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email,
                             password_hash, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId,
                                'Operator', :email, 'hash', true)
                        """)
                .param("id", userId)
                .param("tenantId", tenantId)
                .param("roleId", roleId)
                .param("email", "operator-" + tenantId + "@example.com")
                .update();
        return new Workspace(tenantId, userId, roleId);
    }

    private void authenticate(Workspace workspace, boolean canUpdate) {
        tenantContext.setTenantId(workspace.tenantId());
        List<SimpleGrantedAuthority> authorities = canUpdate
                ? List.of(
                        new SimpleGrantedAuthority(TenantPermissions.DRIVER_READ),
                        new SimpleGrantedAuthority(TenantPermissions.DRIVER_CREATE),
                        new SimpleGrantedAuthority(TenantPermissions.DRIVER_UPDATE),
                        new SimpleGrantedAuthority(TenantPermissions.DRIVER_DELETE),
                        new SimpleGrantedAuthority(TenantPermissions.BOOKING_READ),
                        new SimpleGrantedAuthority(TenantPermissions.BOOKING_CREATE),
                        new SimpleGrantedAuthority(TenantPermissions.BOOKING_UPDATE),
                        new SimpleGrantedAuthority(TenantPermissions.CUSTOMER_READ),
                        new SimpleGrantedAuthority(TenantPermissions.CUSTOMER_CREATE)
                )
                : List.of(new SimpleGrantedAuthority(TenantPermissions.DRIVER_READ));
        TenantPrincipal principal = new TenantPrincipal(
                workspace.userId(),
                workspace.tenantId(),
                "operator@example.com",
                "",
                "Operator",
                null,
                workspace.roleId(),
                "Operations",
                true,
                0,
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private record Workspace(UUID tenantId, UUID userId, UUID roleId) {
    }
}
