package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
import com.rakesh.zentrodesk.inventory.property.api.PropertyRequest;
import com.rakesh.zentrodesk.inventory.property.api.PropertyResponse;
import com.rakesh.zentrodesk.inventory.property.api.PropertyService;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.inventory.reservation.api.AvailabilityView;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationReleaseRequest;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationRequest;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationResponse;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationService;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationTransferRequest;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import com.rakesh.zentrodesk.inventory.room.api.RoomRequest;
import com.rakesh.zentrodesk.inventory.room.api.RoomResponse;
import com.rakesh.zentrodesk.inventory.room.api.RoomService;
import com.rakesh.zentrodesk.operations.booking.api.BookingRequest;
import com.rakesh.zentrodesk.operations.booking.api.BookingResponse;
import com.rakesh.zentrodesk.operations.booking.api.BookingService;
import com.rakesh.zentrodesk.sales.customer.api.CustomerRequest;
import com.rakesh.zentrodesk.sales.customer.api.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomAvailabilityAndReservationsIT extends PostgreSqlIntegrationTest {

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
    private PropertyService propertyService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ReservationService reservationService;

    @AfterEach
    void clearAuthentication() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void providesAvailabilityAndSupportsConflictSafeTransferAndRelease() {
        Inventory inventory = createInventory("availability");
        LocalDate checkIn = LocalDate.now().plusDays(3);
        LocalDate checkOut = checkIn.plusDays(2);

        ReservationResponse reservation = reservationService.create(new ReservationRequest(
                inventory.booking().id(),
                inventory.property().id(),
                inventory.smallRoom().id(),
                checkIn,
                checkOut,
                null,
                null
        ));
        reservationService.create(new ReservationRequest(
                inventory.booking().id(),
                inventory.property().id(),
                inventory.smallRoom().id(),
                checkOut,
                checkOut.plusDays(1),
                null,
                null
        ));

        assertThrows(
                ConflictException.class,
                () -> reservationService.create(new ReservationRequest(
                        inventory.booking().id(),
                        inventory.property().id(),
                        inventory.smallRoom().id(),
                        checkIn.plusDays(1),
                        checkOut.plusDays(1),
                        null,
                        null
                ))
        );
        assertThrows(
                BadRequestException.class,
                () -> reservationService.create(new ReservationRequest(
                        inventory.booking().id(),
                        inventory.property().id(),
                        inventory.largeRoom().id(),
                        LocalDate.now().minusDays(1),
                        LocalDate.now().plusDays(1),
                        null,
                        null
                ))
        );

        var availability = reservationService.availability(
                checkIn,
                AvailabilityView.WEEK,
                inventory.property().id(),
                3
        );
        assertEquals(7, availability.dates().size());
        assertEquals(1, availability.rooms().size());
        assertTrue(availability.rooms().getFirst().availableForEntireRange());

        ReservationResponse transferred = reservationService.transfer(
                reservation.id(),
                new ReservationTransferRequest(inventory.largeRoom().id(), reservation.version())
        );
        assertEquals(inventory.largeRoom().id(), transferred.roomId());
        assertThrows(
                ConflictException.class,
                () -> reservationService.transfer(
                        reservation.id(),
                        new ReservationTransferRequest(inventory.smallRoom().id(), reservation.version())
                )
        );

        ReservationResponse released = reservationService.release(
                transferred.id(),
                new ReservationReleaseRequest(transferred.version())
        );
        ReservationResponse replay = reservationService.release(
                transferred.id(),
                new ReservationReleaseRequest(transferred.version())
        );
        assertEquals(ReservationStatus.CANCELLED, released.status());
        assertFalse(released.active());
        assertEquals(released.id(), replay.id());

        Workspace secondTenant = createWorkspace("isolated");
        authenticate(secondTenant);
        assertThrows(
                ResourceNotFoundException.class,
                () -> reservationService.create(new ReservationRequest(
                        inventory.booking().id(),
                        inventory.property().id(),
                        inventory.largeRoom().id(),
                        checkIn,
                        checkOut,
                        null,
                        null
                ))
        );
    }

    @Test
    void databaseSerializesConcurrentOverlaps() throws Exception {
        Inventory inventory = createInventory("concurrent");
        LocalDate checkIn = LocalDate.now().plusDays(6);
        LocalDate checkOut = checkIn.plusDays(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            insertReservation(first, inventory, checkIn, checkOut);

            Future<String> competingInsert = executor.submit(() -> {
                started.countDown();
                try {
                    insertReservation(second, inventory, checkIn.plusDays(1), checkOut.plusDays(1));
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

    private Inventory createInventory(String suffix) {
        Workspace workspace = createWorkspace(suffix);
        authenticate(workspace);
        UUID customerId = customerService.create(new CustomerRequest(
                "Guest " + suffix,
                "9" + Math.abs(suffix.hashCode()) + "0000",
                "guest-" + suffix + "@example.com",
                null,
                null,
                null,
                null,
                null,
                true,
                null
        )).id();
        BookingResponse booking = bookingService.create(new BookingRequest(
                customerId,
                null,
                null,
                null,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(12),
                null,
                null,
                true,
                new BigDecimal("1000.00"),
                "booking-" + suffix,
                null
        ));
        PropertyResponse property = propertyService.create(new PropertyRequest(
                "Property " + suffix,
                PropertyType.HOTEL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PropertyStatus.ACTIVE,
                true,
                null
        ));
        RoomResponse smallRoom = createRoom(property.id(), "101", 2);
        RoomResponse largeRoom = createRoom(property.id(), "102", 4);
        return new Inventory(workspace, booking, property, smallRoom, largeRoom);
    }

    private RoomResponse createRoom(UUID propertyId, String name, int capacity) {
        return roomService.create(new RoomRequest(
                propertyId,
                name,
                RoomType.STANDARD,
                capacity,
                new BigDecimal("2500.00"),
                null,
                RoomStatus.AVAILABLE,
                true,
                null
        ));
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
                                'Owner', 'Owner', true, 'OWNER')
                        """)
                .param("id", roleId)
                .param("tenantId", tenantId)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email,
                             password_hash, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId,
                                'Owner', :email, 'hash', true)
                        """)
                .param("id", userId)
                .param("tenantId", tenantId)
                .param("roleId", roleId)
                .param("email", "owner-" + tenantId + "@example.com")
                .update();
        return new Workspace(tenantId, userId);
    }

    private void authenticate(Workspace workspace) {
        tenantContext.setTenantId(workspace.tenantId());
        TenantPrincipal principal = new TenantPrincipal(
                workspace.userId(),
                workspace.tenantId(),
                "owner@example.com",
                "",
                "Owner",
                null,
                UUID.randomUUID(),
                "Owner",
                true,
                0,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private void insertReservation(
            Connection connection,
            Inventory inventory,
            LocalDate checkIn,
            LocalDate checkOut
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into zentro_desk.reservations
                    (id, created_at, updated_at, booking_id, property_id, room_id,
                     check_in, check_out, status, active)
                values (?, current_timestamp, current_timestamp, ?, ?, ?, ?, ?, 'RESERVED', true)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, inventory.booking().id());
            statement.setObject(3, inventory.property().id());
            statement.setObject(4, inventory.smallRoom().id());
            statement.setObject(5, checkIn);
            statement.setObject(6, checkOut);
            statement.executeUpdate();
        }
    }

    private record Workspace(UUID tenantId, UUID userId) {
    }

    private record Inventory(
            Workspace workspace,
            BookingResponse booking,
            PropertyResponse property,
            RoomResponse smallRoom,
            RoomResponse largeRoom
    ) {
    }
}
