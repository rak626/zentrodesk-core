package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
import com.rakesh.zentrodesk.inventory.property.api.PropertyRequest;
import com.rakesh.zentrodesk.inventory.property.api.PropertyService;
import com.rakesh.zentrodesk.inventory.property.api.PropertyStatusRequest;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import com.rakesh.zentrodesk.inventory.room.api.RoomRequest;
import com.rakesh.zentrodesk.inventory.room.api.RoomService;
import com.rakesh.zentrodesk.inventory.room.api.RoomStatusRequest;
import com.rakesh.zentrodesk.operations.booking.PaymentMethod;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import com.rakesh.zentrodesk.operations.booking.api.BookingConversionRequest;
import com.rakesh.zentrodesk.operations.booking.api.BookingRequest;
import com.rakesh.zentrodesk.operations.booking.api.BookingService;
import com.rakesh.zentrodesk.operations.booking.api.PaymentRequest;
import com.rakesh.zentrodesk.operations.booking.api.PaymentReversalRequest;
import com.rakesh.zentrodesk.sales.customer.api.CustomerRequest;
import com.rakesh.zentrodesk.sales.customer.api.CustomerResponse;
import com.rakesh.zentrodesk.sales.customer.api.CustomerService;
import com.rakesh.zentrodesk.sales.lead.FollowUpPriority;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import com.rakesh.zentrodesk.sales.lead.api.FollowUpCompletionRequest;
import com.rakesh.zentrodesk.sales.lead.api.FollowUpRequest;
import com.rakesh.zentrodesk.sales.lead.api.FollowUpResponse;
import com.rakesh.zentrodesk.sales.lead.api.FollowUpService;
import com.rakesh.zentrodesk.sales.lead.api.LeadRequest;
import com.rakesh.zentrodesk.sales.lead.api.LeadResponse;
import com.rakesh.zentrodesk.sales.lead.api.LeadService;
import com.rakesh.zentrodesk.sales.lead.api.LeadStatusRequest;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemType;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationItemRequest;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationRequest;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationResponse;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationService;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationStatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerToRoomManagementIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private LeadService leadService;

    @Autowired
    private FollowUpService followUpService;

    @Autowired
    private QuotationService quotationService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private RoomService roomService;

    @AfterEach
    void clearAuthentication() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void normalizesCustomerDuplicatesAndMaintainsLeadTimeline() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse customer = createCustomer("Customer One", "+91 98765-43210");

        assertThrows(
                ConflictException.class,
                () -> createCustomer("Duplicate", "91-98765 43210")
        );

        LeadResponse lead = leadService.create(new LeadRequest(
                workspace.userId(),
                "Traveller",
                "9000000000",
                "traveller@example.com",
                "Referral",
                "Gangtok",
                LocalDate.now().plusDays(20),
                2,
                1,
                new BigDecimal("50000"),
                LeadStatus.NEW,
                "Interested",
                true,
                customer.id(),
                null
        ));
        leadService.updateStatus(lead.id(), new LeadStatusRequest(LeadStatus.CONTACTED, null));
        assertThrows(
                BadRequestException.class,
                () -> leadService.updateStatus(lead.id(), new LeadStatusRequest(LeadStatus.WON, null))
        );

        FollowUpResponse followUp = followUpService.create(new FollowUpRequest(
                lead.id(),
                workspace.userId(),
                Instant.now().plusSeconds(3600),
                FollowUpPriority.HIGH,
                "Confirm travel dates",
                null,
                true,
                null
        ));
        FollowUpResponse completed = followUpService.complete(
                followUp.id(),
                new FollowUpCompletionRequest("Dates confirmed", followUp.version())
        );
        FollowUpResponse replay = followUpService.complete(
                followUp.id(),
                new FollowUpCompletionRequest("Dates confirmed", followUp.version())
        );

        assertTrue(Duration.between(completed.completedAt(), replay.completedAt())
                .abs()
                .compareTo(Duration.ofNanos(1_000)) <= 0);
        assertTrue(followUpService.timeline(lead.id(), 0, 20).totalElements() >= 4);
        Integer taskCount = jdbcClient.sql("""
                        select count(*) from zentro_desk.tasks
                        where tenant_id = :tenantId
                          and source_type = 'LEAD_FOLLOW_UP'
                          and source_id = :sourceId
                        """)
                .param("tenantId", workspace.tenantId())
                .param("sourceId", followUp.id())
                .query(Integer.class)
                .single();
        assertEquals(1, taskCount);
    }

    @Test
    void batchesCustomerRelationshipSummariesForPage() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse relatedCustomer = createCustomer("Related Customer", "8333333333");
        CustomerResponse unrelatedCustomer = createCustomer("Unrelated Customer", "8444444444");

        leadService.create(new LeadRequest(
                workspace.userId(),
                "Related Lead",
                "8555555555",
                "related.lead@example.com",
                "Referral",
                "Gangtok",
                LocalDate.now().plusDays(10),
                2,
                0,
                new BigDecimal("50000"),
                LeadStatus.NEW,
                null,
                true,
                relatedCustomer.id(),
                null
        ));
        quotationService.create(new QuotationRequest(
                null,
                relatedCustomer.id(),
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12),
                2,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now().plusDays(5),
                null,
                null,
                true,
                null,
                List.of(new QuotationItemRequest(
                        QuotationItemType.HOTEL,
                        "Two nights",
                        "night",
                        2,
                        new BigDecimal("100.00"),
                        null
                ))
        ));
        bookingService.create(new BookingRequest(
                relatedCustomer.id(),
                null,
                null,
                null,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12),
                null,
                null,
                true,
                new BigDecimal("200.00"),
                "relationship-summary",
                null
        ));

        var customers = customerService.findAll(0, 20, "name", "asc", null, true);
        CustomerResponse related = customers.content().stream()
                .filter(customer -> customer.id().equals(relatedCustomer.id()))
                .findFirst()
                .orElseThrow();
        CustomerResponse unrelated = customers.content().stream()
                .filter(customer -> customer.id().equals(unrelatedCustomer.id()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, related.relationships().leads());
        assertEquals(1, related.relationships().quotations());
        assertEquals(1, related.relationships().bookings());
        assertEquals(0, unrelated.relationships().leads());
        assertEquals(0, unrelated.relationships().quotations());
        assertEquals(0, unrelated.relationships().bookings());
    }

    @Test
    void calculatesQuotationConvertsOnceAndBalancesIdempotentPayments() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse customer = createCustomer("Quote Customer", "8111111111");
        QuotationResponse quotation = quotationService.create(new QuotationRequest(
                null,
                customer.id(),
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(13),
                2,
                0,
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                LocalDate.now().plusDays(5),
                null,
                null,
                true,
                null,
                List.of(new QuotationItemRequest(
                        QuotationItemType.HOTEL,
                        "Three nights",
                        "night",
                        3,
                        new BigDecimal("35.25"),
                        null
                ))
        ));

        assertEquals(new BigDecimal("100.75"), quotation.total());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, quotation.version())
        );
        QuotationResponse sent = quotationService.findById(quotation.id());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.ACCEPTED, sent.version())
        );

        var booking = bookingService.convertQuotation(
                quotation.id(),
                new BookingConversionRequest("quote-conversion", null)
        );
        var replay = bookingService.convertQuotation(
                quotation.id(),
                new BookingConversionRequest("quote-conversion", null)
        );
        assertEquals(booking.id(), replay.id());
        assertEquals(new BigDecimal("100.75"), booking.agreedValue());

        var first = bookingService.createPayment(booking.id(), new PaymentRequest(
                new BigDecimal("40.00"),
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PAID,
                "TX-ONE",
                Instant.now(),
                null,
                true,
                "payment-one"
        ));
        var firstReplay = bookingService.createPayment(booking.id(), new PaymentRequest(
                new BigDecimal("40.00"),
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PAID,
                "TX-ONE",
                Instant.now(),
                null,
                true,
                "payment-one"
        ));
        assertEquals(first.id(), firstReplay.id());
        assertThrows(
                ConflictException.class,
                () -> bookingService.createPayment(booking.id(), new PaymentRequest(
                        new BigDecimal("61.00"),
                        PaymentMethod.CASH,
                        PaymentStatus.PAID,
                        null,
                        Instant.now(),
                        null,
                        true,
                        "overpay"
                ))
        );
        bookingService.createPayment(booking.id(), new PaymentRequest(
                new BigDecimal("60.75"),
                PaymentMethod.CASH,
                PaymentStatus.PAID,
                null,
                Instant.now(),
                null,
                true,
                "payment-two"
        ));
        assertEquals(PaymentStatus.PAID, bookingService.findById(booking.id()).paymentStatus());

        bookingService.reversePayment(
                first.id(),
                new PaymentReversalRequest("reverse-one", "Entry correction")
        );
        assertEquals(new BigDecimal("40.00"), bookingService.findById(booking.id()).balanceDue());
    }

    @Test
    void summarizesPropertyRoomsAndProtectsActiveReservations() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse customer = createCustomer("Inventory Customer", "8222222222");
        var booking = bookingService.create(new BookingRequest(
                customer.id(),
                null,
                null,
                null,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                null,
                null,
                true,
                new BigDecimal("500.00"),
                "inventory-booking",
                null
        ));
        var property = propertyService.create(new PropertyRequest(
                "Mountain Stay",
                PropertyType.HOTEL,
                "Main Road",
                "Gangtok",
                "Sikkim",
                "India",
                "8000000000",
                "stay@example.com",
                "Host",
                PropertyStatus.ACTIVE,
                true,
                null
        ));
        var room = roomService.create(new RoomRequest(
                property.id(),
                "101",
                RoomType.DELUXE,
                3,
                new BigDecimal("2500.00"),
                "Wi-Fi, breakfast",
                RoomStatus.AVAILABLE,
                true,
                null
        ));

        assertEquals(1, propertyService.findById(property.id()).inventory().totalRooms());
        insertReservation(booking.id(), property.id(), room.id());
        assertThrows(
                ConflictException.class,
                () -> roomService.updateStatus(
                        room.id(),
                        new RoomStatusRequest(RoomStatus.MAINTENANCE, room.version())
                )
        );
        assertThrows(
                ConflictException.class,
                () -> propertyService.updateStatus(
                        property.id(),
                        new PropertyStatusRequest(PropertyStatus.INACTIVE, property.version())
                )
        );
    }

    private CustomerResponse createCustomer(String name, String phone) {
        return customerService.create(new CustomerRequest(
                name,
                phone,
                name.toLowerCase().replace(" ", ".") + "@example.com",
                null,
                "Gangtok",
                "Sikkim",
                "India",
                null,
                true,
                null
        ));
    }

    private Workspace createWorkspace() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String suffix = tenantId.toString().substring(0, 8);
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, :name, :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("name", "Tenant " + suffix)
                .param("code", "tenant-" + suffix)
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
                .param("email", "owner-" + suffix + "@example.com")
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

    private void insertReservation(UUID bookingId, UUID propertyId, UUID roomId) {
        jdbcClient.sql("""
                        insert into zentro_desk.reservations
                            (id, created_at, updated_at, booking_id, property_id, room_id,
                             check_in, check_out, status, active)
                        values (:id, current_timestamp, current_timestamp, :bookingId, :propertyId, :roomId,
                                current_date + 3, current_date + 5, 'RESERVED', true)
                        """)
                .param("id", UUID.randomUUID())
                .param("bookingId", bookingId)
                .param("propertyId", propertyId)
                .param("roomId", roomId)
                .update();
    }

    private record Workspace(UUID tenantId, UUID userId) {
    }
}
