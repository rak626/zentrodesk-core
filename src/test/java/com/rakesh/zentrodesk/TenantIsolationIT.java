package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.analytics.dashboard.DashboardService;
import com.rakesh.zentrodesk.analytics.dashboard.DashboardSummaryResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.conversation.ConversationAssignmentRole;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationRepository;
import com.rakesh.zentrodesk.communication.conversation.api.ConversationAssignmentRequest;
import com.rakesh.zentrodesk.communication.conversation.api.ConversationSendRequest;
import com.rakesh.zentrodesk.communication.conversation.api.CustomerConversationCreateRequest;
import com.rakesh.zentrodesk.communication.conversation.api.CustomerConversationResponse;
import com.rakesh.zentrodesk.communication.conversation.api.CustomerConversationService;
import com.rakesh.zentrodesk.communication.conversation.api.ConversationMessageRequest;
import com.rakesh.zentrodesk.communication.conversation.api.ConversationMessageService;
import com.rakesh.zentrodesk.communication.conversation.api.ConversationStatusRequest;
import com.rakesh.zentrodesk.communication.notification.api.NotificationService;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.inventory.property.api.PropertyService;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationRequest;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationService;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationStatusRequest;
import com.rakesh.zentrodesk.inventory.room.api.RoomService;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import com.rakesh.zentrodesk.operations.booking.api.BookingRequest;
import com.rakesh.zentrodesk.operations.booking.api.BookingService;
import com.rakesh.zentrodesk.operations.booking.api.BookingStatusRequest;
import com.rakesh.zentrodesk.operations.booking.api.PaymentStatusRequest;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import com.rakesh.zentrodesk.operations.driver.api.DriverService;
import com.rakesh.zentrodesk.operations.driver.api.DriverStatusRequest;
import com.rakesh.zentrodesk.operations.task.TaskStatus;
import com.rakesh.zentrodesk.operations.task.api.TaskService;
import com.rakesh.zentrodesk.operations.task.api.TaskStatusRequest;
import com.rakesh.zentrodesk.platform.role.RoleService;
import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import com.rakesh.zentrodesk.platform.user.UserService;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import com.rakesh.zentrodesk.sales.lead.api.LeadService;
import com.rakesh.zentrodesk.sales.lead.api.LeadStatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class TenantIsolationIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ConversationMessageService conversationMessageService;

    @Autowired
    private CustomerConversationService customerConversationService;

    @Autowired
    private CustomerConversationRepository customerConversationRepository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private LeadService leadService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @AfterEach
    void clearTenantAuthentication() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardOnlyShowsNotificationsForCurrentTenantAndUser() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());
        insertNotification(fixture.tenantA(), fixture.userA(), "Visible notification");
        insertNotification(fixture.tenantA(), fixture.otherUserA(), "Other user notification");
        insertNotification(fixture.tenantB(), fixture.userB(), "Other tenant notification");

        DashboardSummaryResponse response = dashboardService.summary(LocalDate.now());

        assertEquals(List.of("Visible notification"), response.activityFeed().stream()
                .map(DashboardSummaryResponse.ActivityItem::title)
                .toList());
    }

    @Test
    void dashboardAggregatesAndQueuesRemainTenantQualified() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());
        insertNotification(fixture.tenantA(), fixture.userA(), "Visible notification");
        insertNotification(fixture.tenantB(), fixture.userB(), "Other tenant notification");
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));

        DashboardSummaryResponse response = dashboardService.summary(
                today.minusDays(1),
                today.plusDays(1),
                "UTC",
                dashboardPrincipal(fixture.tenantA(), fixture.userA())
        );

        assertEquals(1L, response.leadPipelineCounts().get("NEW"));
        assertEquals(List.of("Task A"), response.taskQueue().stream()
                .map(DashboardSummaryResponse.TaskQueueItem::title)
                .toList());
        assertEquals(List.of(fixture.bookingA()), response.allocationWarnings().stream()
                .map(DashboardSummaryResponse.AllocationWarningItem::bookingId)
                .toList());
        assertEquals(List.of("Visible notification"), response.activityFeed().stream()
                .map(DashboardSummaryResponse.ActivityItem::title)
                .toList());
        assertTrue(response.kpis().stream().anyMatch(kpi ->
                "Bookings".equals(kpi.label()) && "1".equals(kpi.value())));
        assertTrue(response.kpis().stream().anyMatch(kpi ->
                "Capacity".equals(kpi.label())
                        && "2 rooms".equals(kpi.value())
                        && "1 drivers available".equals(kpi.detail())));
    }

    @Test
    void notificationMutationRejectsAnotherTenantAndUser() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());
        UUID foreignNotificationId = insertNotification(fixture.tenantB(), fixture.userB(), "Foreign notification");

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markRead(foreignNotificationId, principal(fixture.tenantA(), fixture.userA())));
    }

    @Test
    void bookingCreationRejectsForeignCustomerAndQuotationIds() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());

        BookingRequest foreignCustomer = new BookingRequest(
                fixture.customerB(), null, null, unique("BOOK"), null, null, null, null, null
        );
        BookingRequest foreignQuotation = new BookingRequest(
                fixture.customerA(), fixture.quotationB(), null, unique("BOOK"), null, null, null, null, null
        );

        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(foreignCustomer));
        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(foreignQuotation));
    }

    @Test
    void reservationCreationRejectsForeignBookingAndMismatchedRoom() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = checkIn.plusDays(2);

        ReservationRequest foreignBooking = new ReservationRequest(
                fixture.bookingB(), fixture.propertyA(), fixture.roomA(), checkIn, checkOut, null, null
        );
        ReservationRequest mismatchedRoom = new ReservationRequest(
                fixture.bookingA(), fixture.propertyA(), fixture.otherRoomA(), checkIn, checkOut, null, null
        );

        assertThrows(ResourceNotFoundException.class, () -> reservationService.create(foreignBooking));
        assertThrows(ResourceNotFoundException.class, () -> reservationService.create(mismatchedRoom));
    }

    @Test
    void conversationCreationRejectsForeignCustomerId() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());
        ConversationMessageRequest request = new ConversationMessageRequest(
                fixture.customerB(), "9000000000", ConversationMessageDirection.OUTBOUND, "Tenant boundary test",
                null, null, null
        );

        assertThrows(ResourceNotFoundException.class, () -> conversationMessageService.create(request));
    }

    @Test
    void tenantEmployeesShareOneConfiguredSenderAndSupportMultipleAssignees() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());
        jdbcClient.sql("""
                        insert into zentro_desk.whatsapp_configurations (
                            id, created_at, updated_at, tenant_id, waba_id, business_id,
                            phone_number_id, display_phone_number, verified_name,
                            connection_method, access_token_ciphertext, funding_status,
                            token_healthy, status, last_validated_at, last_healthy_at, version
                        ) values (
                            :id, now(), now(), :tenantId, 'waba-tenant-a', 'business-tenant-a',
                            'phone-number-tenant-a', '+91 90000 00000', 'Tenant A',
                            'MANUAL', 'encrypted-test-token', 'FUNDED',
                            true, 'CONNECTED', now(), now(), 0
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("tenantId", fixture.tenantA())
                .update();
        CustomerConversationResponse conversation = customerConversationService.create(
                new CustomerConversationCreateRequest(null, "+91 98888 88888", "WhatsApp Customer")
        );
        var persisted = customerConversationRepository.findByIdAndTenantId(
                conversation.id(),
                fixture.tenantA()
        ).orElseThrow();
        persisted.setLastInboundAt(Instant.now());
        customerConversationRepository.save(persisted);

        customerConversationService.assign(
                conversation.id(),
                new ConversationAssignmentRequest(fixture.userA(), Set.of(fixture.otherUserA()))
        );
        customerConversationService.send(conversation.id(), new ConversationSendRequest("Owner reply"));

        authenticate(fixture.tenantA(), fixture.otherUserA());
        customerConversationService.send(conversation.id(), new ConversationSendRequest("Team reply"));

        assertEquals(1, jdbcClient.sql("""
                        select count(*) from zentro_desk.whatsapp_configurations
                        where tenant_id = :tenantId and phone_number_id = 'phone-number-tenant-a'
                        """)
                .param("tenantId", fixture.tenantA())
                .query(Integer.class)
                .single());
        assertEquals(2, customerConversationService.messages(conversation.id()).stream()
                .filter(message -> message.direction() == ConversationMessageDirection.OUTBOUND)
                .map(message -> message.sentByUserId())
                .distinct()
                .count());
        CustomerConversationResponse assigned = customerConversationService.findAll(
                        0, 20, "lastMessageAt", "DESC", null, null
                ).content().stream()
                .filter(item -> item.id().equals(conversation.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(assigned.assignments().stream().anyMatch(item ->
                item.userId().equals(fixture.userA()) && item.role() == ConversationAssignmentRole.PRIMARY));
        assertTrue(assigned.assignments().stream().anyMatch(item ->
                item.userId().equals(fixture.otherUserA())
                        && item.role() == ConversationAssignmentRole.COLLABORATOR));

        authenticate(fixture.tenantB(), fixture.userB());
        assertThrows(ResourceNotFoundException.class, () ->
                customerConversationService.messages(conversation.id()));
    }

    @Test
    void tenantQualifiedReadsAndMutationsHideForeignRecords() {
        Fixture fixture = createFixture();
        authenticate(fixture.tenantA(), fixture.userA());

        assertDoesNotThrow(() -> propertyService.findById(fixture.propertyA()));
        assertDoesNotThrow(() -> roomService.findById(fixture.roomA()));
        assertDoesNotThrow(() -> driverService.findById(fixture.driverA()));
        assertDoesNotThrow(() -> leadService.findById(fixture.leadA()));
        assertDoesNotThrow(() -> bookingService.findById(fixture.bookingA()));
        assertDoesNotThrow(() -> userService.findById(fixture.userA()));
        assertDoesNotThrow(() -> roleService.findById(fixture.roleA()));
        assertDoesNotThrow(() -> taskService.updateStatus(fixture.taskA(), new TaskStatusRequest(TaskStatus.COMPLETED)));
        assertDoesNotThrow(() -> reservationService.updateStatus(
                fixture.reservationA(), new ReservationStatusRequest(ReservationStatus.CHECKED_IN)
        ));
        assertDoesNotThrow(() -> conversationMessageService.updateStatus(
                fixture.conversationA(), new ConversationStatusRequest(ConversationMessageStatus.SENT)
        ));
        assertDoesNotThrow(() -> bookingService.updatePaymentStatus(
                fixture.paymentA(), new PaymentStatusRequest(PaymentStatus.PAID)
        ));

        assertThrows(ResourceNotFoundException.class, () -> propertyService.findById(fixture.propertyB()));
        assertThrows(ResourceNotFoundException.class, () -> roomService.findById(fixture.roomB()));
        assertThrows(ResourceNotFoundException.class, () -> userService.revokeSessions(fixture.userB()));
        assertThrows(ResourceNotFoundException.class,
                () -> driverService.updateStatus(fixture.driverB(), new DriverStatusRequest(DriverStatus.UNAVAILABLE)));
        assertThrows(ResourceNotFoundException.class,
                () -> leadService.updateStatus(fixture.leadB(), new LeadStatusRequest(LeadStatus.QUALIFIED)));
        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.updateStatus(fixture.bookingB(), new BookingStatusRequest(BookingStatus.CANCELLED)));
        assertThrows(ResourceNotFoundException.class, () -> driverService.delete(fixture.driverB()));
        assertThrows(ResourceNotFoundException.class,
                () -> taskService.updateStatus(fixture.taskB(), new TaskStatusRequest(TaskStatus.COMPLETED)));
        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.updateStatus(
                        fixture.reservationB(), new ReservationStatusRequest(ReservationStatus.CANCELLED)
                ));
        assertThrows(ResourceNotFoundException.class,
                () -> conversationMessageService.updateStatus(
                        fixture.conversationB(), new ConversationStatusRequest(ConversationMessageStatus.FAILED)
                ));
        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.updatePaymentStatus(
                        fixture.paymentB(), new PaymentStatusRequest(PaymentStatus.PAID)
                ));
        assertThrows(ResourceNotFoundException.class, () -> userService.findById(fixture.userB()));
        assertThrows(ResourceNotFoundException.class, () -> roleService.findById(fixture.roleB()));
    }

    private Fixture createFixture() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID otherUserA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID quotationB = UUID.randomUUID();
        UUID propertyA = UUID.randomUUID();
        UUID otherPropertyA = UUID.randomUUID();
        UUID roomA = UUID.randomUUID();
        UUID otherRoomA = UUID.randomUUID();
        UUID bookingA = UUID.randomUUID();
        UUID bookingB = UUID.randomUUID();
        UUID driverA = UUID.randomUUID();
        UUID driverB = UUID.randomUUID();
        UUID leadA = UUID.randomUUID();
        UUID leadB = UUID.randomUUID();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID conversationA = UUID.randomUUID();
        UUID conversationB = UUID.randomUUID();
        UUID reservationA = UUID.randomUUID();
        UUID reservationB = UUID.randomUUID();
        UUID propertyB = UUID.randomUUID();
        UUID roomB = UUID.randomUUID();
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();

        insertTenant(tenantA, unique("tenant-a"));
        insertTenant(tenantB, unique("tenant-b"));
        insertRole(roleA, tenantA, unique("role-a"));
        insertRole(roleB, tenantB, unique("role-b"));
        insertUser(userA, tenantA, roleA, unique("user-a") + "@example.com");
        insertUser(otherUserA, tenantA, roleA, unique("user-a2") + "@example.com");
        insertUser(userB, tenantB, roleB, unique("user-b") + "@example.com");
        insertCustomer(customerA, tenantA, "Customer A");
        insertCustomer(customerB, tenantB, "Customer B");
        insertQuotation(quotationB, tenantB, customerB);
        insertProperty(propertyA, tenantA, "Property A");
        insertProperty(otherPropertyA, tenantA, "Property A2");
        insertRoom(roomA, propertyA, "Room A");
        insertRoom(otherRoomA, otherPropertyA, "Room A2");
        insertBooking(bookingA, tenantA, customerA);
        insertBooking(bookingB, tenantB, customerB);
        insertDriver(driverA, tenantA, "Driver A");
        insertDriver(driverB, tenantB, "Driver B");
        insertLead(leadA, tenantA, userA, "Lead A");
        insertLead(leadB, tenantB, userB, "Lead B");
        insertTask(taskA, tenantA, userA, "Task A");
        insertTask(taskB, tenantB, userB, "Task B");
        insertConversation(conversationA, tenantA, customerA);
        insertConversation(conversationB, tenantB, customerB);
        insertReservation(reservationA, bookingA, propertyA, roomA);
        insertProperty(propertyB, tenantB, "Property B");
        insertRoom(roomB, propertyB, "Room B");
        insertReservation(reservationB, bookingB, propertyB, roomB);
        insertPayment(paymentA, bookingA);
        insertPayment(paymentB, bookingB);

        return new Fixture(tenantA, tenantB, roleA, roleB, userA, otherUserA, userB, customerA, customerB,
                quotationB, propertyA, propertyB, roomA, roomB, otherRoomA, bookingA, bookingB, driverA, driverB,
                leadA, leadB, taskA, taskB, conversationA, conversationB, reservationA, reservationB, paymentA,
                paymentB);
    }

    private void authenticate(UUID tenantId, UUID userId) {
        tenantContext.setTenantId(tenantId);
        TenantPrincipal principal = principal(tenantId, userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private TenantPrincipal principal(UUID tenantId, UUID userId) {
        return new TenantPrincipal(userId, tenantId, "tenant-user@example.com", "", "Tenant", "User",
                UUID.randomUUID(), "Owner", true, 0, List.of());
    }

    private TenantPrincipal dashboardPrincipal(UUID tenantId, UUID userId) {
        return new TenantPrincipal(
                userId,
                tenantId,
                "tenant-user@example.com",
                "",
                "Tenant",
                "User",
                UUID.randomUUID(),
                FixedTenantRole.OWNER.displayName(),
                true,
                0,
                FixedTenantRole.OWNER.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }

    private void insertTenant(UUID id, String code) {
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, :name, :code, 'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", id)
                .param("name", code)
                .param("code", code)
                .update();
    }

    private void insertRole(UUID id, UUID tenantId, String name) {
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_roles
                            (id, created_at, updated_at, tenant_id, name, template_code, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, 'OWNER', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name)
                .update();
    }

    private void insertUser(UUID id, UUID tenantId, UUID roleId, String email) {
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email, password_hash, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId, 'Tenant', :email, 'unused', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("roleId", roleId)
                .param("email", email)
                .update();
    }

    private void insertCustomer(UUID id, UUID tenantId, String name) {
        jdbcClient.sql("""
                        insert into zentro_desk.customers
                            (id, created_at, updated_at, tenant_id, name, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name)
                .update();
    }

    private void insertQuotation(UUID id, UUID tenantId, UUID customerId) {
        jdbcClient.sql("""
                        insert into zentro_desk.quotations
                            (id, created_at, updated_at, tenant_id, customer_id, quotation_number, total, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :customerId, :number, 1000, 'DRAFT', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("customerId", customerId)
                .param("number", unique("QUOTE"))
                .update();
    }

    private void insertProperty(UUID id, UUID tenantId, String name) {
        jdbcClient.sql("""
                        insert into zentro_desk.properties
                            (id, created_at, updated_at, tenant_id, name, type, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, 'HOTEL', 'ACTIVE', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name)
                .update();
    }

    private void insertRoom(UUID id, UUID propertyId, String name) {
        jdbcClient.sql("""
                        insert into zentro_desk.rooms
                            (id, created_at, updated_at, property_id, name, type, capacity, base_price, status, active)
                        values (:id, current_timestamp, current_timestamp, :propertyId, :name, 'STANDARD', 2, 1000, 'AVAILABLE', true)
                        """)
                .param("id", id)
                .param("propertyId", propertyId)
                .param("name", name)
                .update();
    }

    private void insertBooking(UUID id, UUID tenantId, UUID customerId) {
        jdbcClient.sql("""
                        insert into zentro_desk.bookings
                            (id, created_at, updated_at, tenant_id, customer_id, booking_number, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :customerId, :number, 'CONFIRMED', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("customerId", customerId)
                .param("number", unique("BOOK"))
                .update();
    }

    private void insertDriver(UUID id, UUID tenantId, String name) {
        jdbcClient.sql("""
                        insert into zentro_desk.drivers
                            (id, created_at, updated_at, tenant_id, name, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, 'AVAILABLE', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name)
                .update();
    }

    private void insertLead(UUID id, UUID tenantId, UUID assignedUserId, String name) {
        jdbcClient.sql("""
                        insert into zentro_desk.leads
                            (id, created_at, updated_at, tenant_id, assigned_user_id, name, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :userId, :name, 'NEW', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("userId", assignedUserId)
                .param("name", name)
                .update();
    }

    private void insertTask(UUID id, UUID tenantId, UUID userId, String title) {
        jdbcClient.sql("""
                        insert into zentro_desk.tasks
                            (id, created_at, updated_at, tenant_id, user_id, title, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :userId, :title, 'PENDING', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("title", title)
                .update();
    }

    private void insertConversation(UUID id, UUID tenantId, UUID customerId) {
        UUID conversationId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.customer_conversations
                            (id, created_at, updated_at, tenant_id, customer_id, phone, normalized_phone,
                             status, unread_count, active, version)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :customerId,
                                '9000000000', '9000000000', 'OPEN', 0, true, 0)
                        """)
                .param("id", conversationId)
                .param("tenantId", tenantId)
                .param("customerId", customerId)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.conversation_messages
                            (id, created_at, updated_at, tenant_id, conversation_id, customer_id, phone,
                             direction, message, message_type, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :conversationId,
                                :customerId, '9000000000', 'OUTBOUND', 'Test', 'TEXT', 'PENDING', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("conversationId", conversationId)
                .param("customerId", customerId)
                .update();
    }

    private void insertReservation(UUID id, UUID bookingId, UUID propertyId, UUID roomId) {
        jdbcClient.sql("""
                        insert into zentro_desk.reservations
                            (id, created_at, updated_at, booking_id, property_id, room_id, check_in, check_out, status, active)
                        values (:id, current_timestamp, current_timestamp, :bookingId, :propertyId, :roomId,
                                current_date + 5, current_date + 7, 'RESERVED', true)
                        """)
                .param("id", id)
                .param("bookingId", bookingId)
                .param("propertyId", propertyId)
                .param("roomId", roomId)
                .update();
    }

    private void insertPayment(UUID id, UUID bookingId) {
        jdbcClient.sql("""
                        insert into zentro_desk.payments
                            (id, created_at, updated_at, booking_id, amount, method, status, active)
                        values (:id, current_timestamp, current_timestamp, :bookingId, 100, 'CASH', 'PENDING', true)
                        """)
                .param("id", id)
                .param("bookingId", bookingId)
                .update();
    }

    private UUID insertNotification(UUID tenantId, UUID userId, String title) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.notifications
                            (id, created_at, updated_at, tenant_id, user_id, title, message, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :userId, :title, 'Test', 'UNREAD', true)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("title", title)
                .update();
        return id;
    }

    private String unique(String prefix) {
        return prefix + '-' + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(
            UUID tenantA,
            UUID tenantB,
            UUID roleA,
            UUID roleB,
            UUID userA,
            UUID otherUserA,
            UUID userB,
            UUID customerA,
            UUID customerB,
            UUID quotationB,
            UUID propertyA,
            UUID propertyB,
            UUID roomA,
            UUID roomB,
            UUID otherRoomA,
            UUID bookingA,
            UUID bookingB,
            UUID driverA,
            UUID driverB,
            UUID leadA,
            UUID leadB,
            UUID taskA,
            UUID taskB,
            UUID conversationA,
            UUID conversationB,
            UUID reservationA,
            UUID reservationB,
            UUID paymentA,
            UUID paymentB
    ) {
    }
}
