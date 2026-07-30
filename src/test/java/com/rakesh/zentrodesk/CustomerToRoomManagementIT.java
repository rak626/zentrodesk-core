package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
import com.rakesh.zentrodesk.inventory.property.api.PropertyRequest;
import com.rakesh.zentrodesk.inventory.property.api.PropertyService;
import com.rakesh.zentrodesk.communication.conversation.api.CustomerConversationCreateRequest;
import com.rakesh.zentrodesk.communication.conversation.api.CustomerConversationService;
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
import com.rakesh.zentrodesk.sales.quotation.QuotationPricingBasis;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationItemRequest;
import com.rakesh.zentrodesk.sales.quotation.api.QuotationItemScheduleRequest;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private CustomerConversationService customerConversationService;

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
                        QuotationItemType.MISCELLANEOUS,
                        null,
                        "Two nights",
                        "night",
                        2,
                        new BigDecimal("100.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
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
    void quotationCreatorTakesOwnershipOfTheLinkedLeadAndChatWithHistory() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse customer = createCustomer("Ownership Customer", "8111111111");
        LeadResponse lead = leadService.create(new LeadRequest(
                null,
                "Ownership Lead",
                "8111111111",
                null,
                "Referral",
                "Gangtok",
                LocalDate.now().plusDays(10),
                2,
                0,
                new BigDecimal("50000"),
                LeadStatus.NEW,
                null,
                true,
                customer.id(),
                null
        ));
        var conversation = customerConversationService.create(
                new CustomerConversationCreateRequest(lead.id(), customer.id(), null, null)
        );
        UUID employeeB = createEmployee(workspace, "Employee B");

        authenticate(workspace, employeeB, "Sales");
        QuotationResponse quotation = createLeadQuotation(lead.id());

        assertEquals(employeeB, quotation.assignedUserId());
        assertEquals(employeeB, leadService.findById(lead.id()).assignedUserId());
        assertEquals(1, jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.conversation_assignments
                        where conversation_id = :conversationId
                          and user_id = :ownerId
                          and assignment_role = 'PRIMARY'
                        """)
                .param("conversationId", conversation.id())
                .param("ownerId", employeeB)
                .query(Integer.class)
                .single());
        assertEquals(1, jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.ownership_history
                        where source_type = 'QUOTATION'
                          and source_id = :quotationId
                          and previous_owner_id = :previousOwnerId
                          and owner_id = :ownerId
                        """)
                .param("quotationId", quotation.id())
                .param("previousOwnerId", workspace.userId())
                .param("ownerId", employeeB)
                .query(Integer.class)
                .single());
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
                        QuotationItemType.MISCELLANEOUS,
                        null,
                        "Three nights",
                        "night",
                        3,
                        new BigDecimal("35.25"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        ));

        assertEquals(new BigDecimal("100.75"), quotation.total());
        assertEquals("PENDING", jdbcClient.sql("""
                        select generation_status
                        from zentro_desk.quotation_documents
                        where tenant_id = :tenantId and quotation_id = :quotationId
                        order by document_version desc
                        limit 1
                        """)
                .param("tenantId", workspace.tenantId())
                .param("quotationId", quotation.id())
                .query(String.class)
                .single());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, quotation.version())
        );
        assertEquals("PENDING", jdbcClient.sql("""
                        select delivery_status
                        from zentro_desk.quotation_documents
                        where tenant_id = :tenantId and quotation_id = :quotationId
                        order by document_version desc
                        limit 1
                        """)
                .param("tenantId", workspace.tenantId())
                .param("quotationId", quotation.id())
                .query(String.class)
                .single());
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
    void calculatesAutomaticTripCostsAndPreservesSentRevision() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse customer = createCustomer("Revision Customer", "8222222222");
        QuotationResponse quotation = quotationService.create(new QuotationRequest(
                null,
                customer.id(),
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(14),
                4,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now().plusDays(5),
                null,
                null,
                true,
                null,
                List.of(
                        new QuotationItemRequest(
                                QuotationItemType.MISCELLANEOUS,
                                QuotationPricingBasis.PER_PERSON,
                                "Hill View Homestay",
                                "night",
                                1,
                                new BigDecimal("1400.00"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        new QuotationItemRequest(
                                QuotationItemType.MISCELLANEOUS,
                                QuotationPricingBasis.PER_PERSON,
                                "Srikhola Homestay",
                                "night",
                                2,
                                new BigDecimal("1400.00"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        new QuotationItemRequest(
                                QuotationItemType.MISCELLANEOUS,
                                QuotationPricingBasis.PER_PERSON,
                                "Pelling Homestay",
                                "night",
                                1,
                                new BigDecimal("1800.00"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        new QuotationItemRequest(
                                QuotationItemType.MISCELLANEOUS,
                                QuotationPricingBasis.PER_VEHICLE,
                                "NJP to Hill View",
                                "trip",
                                1,
                                new BigDecimal("3800.00"),
                                null,
                                4,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        ));

        assertEquals(new BigDecimal("31600.00"), quotation.total());
        assertEquals(new BigDecimal("7900.00"), quotation.perPersonTotal());
        assertEquals(4, quotation.items().getFirst().chargeablePeople());
        assertEquals(2, quotation.items().getLast().vehicleCount());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, quotation.version())
        );

        QuotationResponse revision = quotationService.createRevision(quotation.id());

        assertEquals(quotation.quotationNumber(), revision.quotationNumber());
        assertEquals(quotation.seriesId(), revision.seriesId());
        assertEquals(2, revision.revisionNumber());
        assertEquals(quotation.id(), revision.revisedFromId());
        assertEquals(QuotationStatus.DRAFT, revision.status());
        assertEquals(4, revision.items().size());
        assertTrue(quotationService.findById(quotation.id()).superseded());
        assertThrows(
                ConflictException.class,
                () -> quotationService.updateStatus(
                        quotation.id(),
                        new QuotationStatusRequest(QuotationStatus.ACCEPTED, null)
                )
        );
    }

    @Test
    void createsCustomerFromLeadAndLinksQuotationRevisionsDuringBookingConversion() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        LeadResponse lead = leadService.create(new LeadRequest(
                workspace.userId(),
                "Lead Traveller",
                "8333333333",
                "lead.traveller@example.com",
                "Referral",
                "Pelling",
                LocalDate.now().plusDays(10),
                2,
                1,
                new BigDecimal("30000.00"),
                LeadStatus.QUOTATION_SENT,
                null,
                true,
                null,
                null
        ));
        QuotationResponse original = createLeadQuotation(lead.id());
        quotationService.updateStatus(
                original.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, original.version())
        );
        QuotationResponse revision = quotationService.createRevision(original.id());
        quotationService.updateStatus(
                revision.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, revision.version())
        );
        QuotationResponse sentRevision = quotationService.findById(revision.id());
        quotationService.updateStatus(
                revision.id(),
                new QuotationStatusRequest(QuotationStatus.ACCEPTED, sentRevision.version())
        );

        var booking = bookingService.convertQuotation(
                revision.id(),
                new BookingConversionRequest("lead-conversion", null)
        );

        assertNotNull(booking.customerId());
        CustomerResponse customer = customerService.findById(booking.customerId());
        assertEquals(lead.name(), customer.name());
        assertEquals(lead.phone(), customer.phone());
        assertEquals(lead.email(), customer.email());
        assertEquals(1, customer.relationships().leads());
        assertEquals(2, customer.relationships().quotations());
        assertEquals(1, customer.relationships().bookings());
        assertEquals(booking.customerId(), leadService.findById(lead.id()).customerId());
        assertEquals(LeadStatus.WON, leadService.findById(lead.id()).status());
        assertEquals(booking.customerId(), quotationService.findById(original.id()).customerId());
        assertEquals(booking.customerId(), quotationService.findById(revision.id()).customerId());
    }

    @Test
    void reusesMatchingCustomerWhenLeadQuotationBecomesBooking() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse existing = createCustomer("Existing Customer", "8444444444");
        LeadResponse lead = leadService.create(new LeadRequest(
                workspace.userId(),
                "Returning Traveller",
                "84 4444 4444",
                existing.email(),
                "Repeat enquiry",
                "Gangtok",
                LocalDate.now().plusDays(10),
                2,
                0,
                new BigDecimal("20000.00"),
                LeadStatus.QUOTATION_SENT,
                null,
                true,
                null,
                null
        ));
        QuotationResponse quotation = createLeadQuotation(lead.id());
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
                new BookingConversionRequest("existing-customer-conversion", null)
        );

        assertEquals(existing.id(), booking.customerId());
        assertEquals(existing.id(), leadService.findById(lead.id()).customerId());
        assertEquals(1, customerService.findById(existing.id()).relationships().bookings());
    }

    @Test
    void rejectsAutomaticConversionWhenLeadContactsMatchDifferentCustomers() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse phoneMatch = createCustomer("Phone Match", "8555555555");
        CustomerResponse emailMatch = createCustomer("Email Match", "8666666666");
        LeadResponse lead = leadService.create(new LeadRequest(
                workspace.userId(),
                "Ambiguous Traveller",
                phoneMatch.phone(),
                emailMatch.email(),
                "Referral",
                "Darjeeling",
                LocalDate.now().plusDays(10),
                2,
                0,
                new BigDecimal("20000.00"),
                LeadStatus.QUOTATION_SENT,
                null,
                true,
                null,
                null
        ));
        QuotationResponse quotation = createLeadQuotation(lead.id());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, quotation.version())
        );
        QuotationResponse sent = quotationService.findById(quotation.id());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.ACCEPTED, sent.version())
        );

        ConflictException conflict = assertThrows(
                ConflictException.class,
                () -> bookingService.convertQuotation(
                        quotation.id(),
                        new BookingConversionRequest("ambiguous-customer-conversion", null)
                )
        );

        assertEquals(
                "Lead phone and email match different customers; link the customer manually",
                conflict.getMessage()
        );
    }

    @Test
    void atomicallyBlocksSelectedQuotationResourcesWhenConvertedToBooking() {
        Workspace workspace = createWorkspace();
        authenticate(workspace);
        CustomerResponse customer = createCustomer("Resource Traveller", "8777777777");
        LocalDate travelStart = LocalDate.now().plusDays(20);
        LocalDate travelEnd = travelStart.plusDays(2);
        var property = propertyService.create(new PropertyRequest(
                "Hill View",
                PropertyType.HOMESTAY,
                null,
                "Darjeeling",
                "West Bengal",
                "India",
                null,
                null,
                null,
                PropertyStatus.ACTIVE,
                true,
                null
        ));
        var room = roomService.create(new RoomRequest(
                property.id(),
                "Deluxe 1",
                RoomType.DELUXE,
                4,
                new BigDecimal("1400.00"),
                "Mountain view",
                RoomStatus.AVAILABLE,
                true,
                null
        ));
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.vehicles
                            (id, created_at, updated_at, tenant_id, registration_number, type,
                             capacity, status, active, version)
                        values (:id, current_timestamp, current_timestamp, :tenantId, 'WB-01-TEST',
                                'SUV', 4, 'AVAILABLE', true, 0)
                        """)
                .param("id", vehicleId)
                .param("tenantId", workspace.tenantId())
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.drivers
                            (id, created_at, updated_at, tenant_id, name, status, active, vehicle_id, version)
                        values (:id, current_timestamp, current_timestamp, :tenantId,
                                'Fixed Driver', 'AVAILABLE', true, :vehicleId, 0)
                        """)
                .param("id", driverId)
                .param("tenantId", workspace.tenantId())
                .param("vehicleId", vehicleId)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.guides
                            (id, created_at, updated_at, tenant_id, name, languages, base_rate,
                             status, active, version)
                        values (:id, current_timestamp, current_timestamp, :tenantId,
                                'Local Guide', 'English, Hindi', 1200, 'AVAILABLE', true, 0)
                        """)
                .param("id", guideId)
                .param("tenantId", workspace.tenantId())
                .update();

        QuotationResponse first = createResourceQuotation(
                customer.id(),
                travelStart,
                travelEnd,
                room.id(),
                vehicleId,
                guideId
        );
        accept(first);
        bookingService.convertQuotation(first.id(), new BookingConversionRequest("resource-booking-1", null));

        assertEquals(1, activeResourceCount("reservations", workspace.tenantId()));
        assertEquals(1, activeResourceCount("driver_assignments", workspace.tenantId()));
        assertEquals(1, activeResourceCount("guide_assignments", workspace.tenantId()));

        QuotationResponse competing = createResourceQuotation(
                customer.id(),
                travelStart,
                travelEnd,
                room.id(),
                vehicleId,
                guideId
        );
        accept(competing);
        assertThrows(
                ConflictException.class,
                () -> bookingService.convertQuotation(
                        competing.id(),
                        new BookingConversionRequest("resource-booking-2", null)
                )
        );
        Integer competingBookings = jdbcClient.sql("""
                        select count(*) from zentro_desk.bookings
                        where tenant_id = :tenantId and quotation_id = :quotationId
                        """)
                .param("tenantId", workspace.tenantId())
                .param("quotationId", competing.id())
                .query(Integer.class)
                .single();
        assertEquals(0, competingBookings);
        assertEquals(1, activeResourceCount("reservations", workspace.tenantId()));
        assertEquals(1, activeResourceCount("driver_assignments", workspace.tenantId()));
        assertEquals(1, activeResourceCount("guide_assignments", workspace.tenantId()));
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

    private QuotationResponse createLeadQuotation(UUID leadId) {
        return quotationService.create(new QuotationRequest(
                leadId,
                null,
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
                        QuotationItemType.MISCELLANEOUS,
                        QuotationPricingBasis.PER_PERSON,
                        "Two-night stay",
                        "night",
                        2,
                        new BigDecimal("1500.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        ));
    }

    private QuotationResponse createResourceQuotation(
            UUID customerId,
            LocalDate travelStart,
            LocalDate travelEnd,
            UUID roomId,
            UUID vehicleId,
            UUID guideId
    ) {
        Instant startsAt = travelStart.atTime(8, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant endsAt = travelEnd.atTime(18, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        return quotationService.create(new QuotationRequest(
                null,
                customerId,
                travelStart,
                travelEnd,
                4,
                2,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                travelStart.minusDays(1),
                null,
                null,
                true,
                null,
                List.of(
                        new QuotationItemRequest(
                                QuotationItemType.HOTEL,
                                QuotationPricingBasis.PER_PERSON,
                                "Hill View Deluxe",
                                "night",
                                1,
                                new BigDecimal("1400.00"),
                                null,
                                null,
                                null,
                                new QuotationItemScheduleRequest(
                                        travelStart,
                                        travelEnd,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                List.of(roomId),
                                false,
                                null
                        ),
                        new QuotationItemRequest(
                                QuotationItemType.VEHICLE,
                                QuotationPricingBasis.PER_VEHICLE,
                                "NJP to Hill View",
                                "trip",
                                1,
                                new BigDecimal("3800.00"),
                                null,
                                4,
                                null,
                                new QuotationItemScheduleRequest(
                                        null,
                                        null,
                                        startsAt,
                                        endsAt,
                                        "NJP",
                                        "Hill View"
                                ),
                                List.of(vehicleId),
                                false,
                                null
                        ),
                        new QuotationItemRequest(
                                QuotationItemType.GUIDE,
                                QuotationPricingBasis.FIXED,
                                "Local guide",
                                "service",
                                1,
                                new BigDecimal("1200.00"),
                                null,
                                null,
                                null,
                                new QuotationItemScheduleRequest(
                                        null,
                                        null,
                                        startsAt,
                                        endsAt,
                                        null,
                                        null
                                ),
                                List.of(guideId),
                                false,
                                null
                        )
                )
        ));
    }

    private void accept(QuotationResponse quotation) {
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.SENT, quotation.version())
        );
        QuotationResponse sent = quotationService.findById(quotation.id());
        quotationService.updateStatus(
                quotation.id(),
                new QuotationStatusRequest(QuotationStatus.ACCEPTED, sent.version())
        );
    }

    private int activeResourceCount(String table, UUID tenantId) {
        String sql = switch (table) {
            case "reservations" -> """
                    select count(*) from zentro_desk.reservations reservation
                    join zentro_desk.properties property on property.id = reservation.property_id
                    where property.tenant_id = :tenantId and reservation.active = true
                    """;
            case "driver_assignments" -> """
                    select count(*) from zentro_desk.driver_assignments
                    where tenant_id = :tenantId and active = true
                    """;
            case "guide_assignments" -> """
                    select count(*) from zentro_desk.guide_assignments
                    where tenant_id = :tenantId and active = true
                    """;
            default -> throw new IllegalArgumentException("Unsupported resource table");
        };
        return jdbcClient.sql(sql)
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single();
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
        authenticate(workspace, workspace.userId(), "Owner");
    }

    private void authenticate(Workspace workspace, UUID userId, String roleName) {
        tenantContext.setTenantId(workspace.tenantId());
        TenantPrincipal principal = new TenantPrincipal(
                userId,
                workspace.tenantId(),
                "owner@example.com",
                "",
                roleName,
                null,
                UUID.randomUUID(),
                roleName,
                true,
                0,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private UUID createEmployee(Workspace workspace, String name) {
        UUID userId = UUID.randomUUID();
        UUID roleId = jdbcClient.sql("""
                        select id
                        from zentro_desk.tenant_roles
                        where tenant_id = :tenantId
                          and template_code = 'OWNER'
                        """)
                .param("tenantId", workspace.tenantId())
                .query(UUID.class)
                .single();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email,
                             password_hash, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId,
                                :name, :email, 'hash', true)
                        """)
                .param("id", userId)
                .param("tenantId", workspace.tenantId())
                .param("roleId", roleId)
                .param("name", name)
                .param("email", userId + "@example.com")
                .update();
        return userId;
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
