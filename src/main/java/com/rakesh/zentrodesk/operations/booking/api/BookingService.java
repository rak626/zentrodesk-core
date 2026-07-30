package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingResourcesRequestedEvent;
import com.rakesh.zentrodesk.operations.booking.BookingGuest;
import com.rakesh.zentrodesk.operations.booking.BookingGuestRepository;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.booking.Payment;
import com.rakesh.zentrodesk.operations.booking.PaymentRepository;
import com.rakesh.zentrodesk.operations.booking.PaymentRecordedEvent;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import com.rakesh.zentrodesk.operations.assignment.api.AssignmentService;
import com.rakesh.zentrodesk.operations.driver.Driver;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.platform.audit.AuditEventResponse;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.LeadCustomerConversionService;
import com.rakesh.zentrodesk.sales.customer.SalesReferenceService;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationItem;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemResource;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemResourceRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt",
            "updatedAt",
            "bookingNumber",
            "travelStart",
            "travelEnd",
            "agreedValue",
            "status",
            "active"
    );
    private static final List<PaymentStatus> PAID_STATUSES =
            List.of(PaymentStatus.PAID, PaymentStatus.PARTIAL);
    private static final Map<BookingStatus, Set<BookingStatus>> BOOKING_TRANSITIONS = Map.of(
            BookingStatus.CONFIRMED, Set.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED),
            BookingStatus.IN_PROGRESS, Set.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED),
            BookingStatus.COMPLETED, Set.of(),
            BookingStatus.CANCELLED, Set.of()
    );
    private static final Map<PaymentStatus, Set<PaymentStatus>> PAYMENT_TRANSITIONS = Map.of(
            PaymentStatus.PENDING, Set.of(PaymentStatus.PARTIAL, PaymentStatus.PAID, PaymentStatus.FAILED),
            PaymentStatus.PARTIAL, Set.of(PaymentStatus.PAID, PaymentStatus.REFUNDED),
            PaymentStatus.PAID, Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED, Set.of(),
            PaymentStatus.REFUNDED, Set.of()
    );

    private final BookingRepository bookingRepository;
    private final BookingGuestRepository guestRepository;
    private final PaymentRepository paymentRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final QuotationItemResourceRepository quotationItemResourceRepository;
    private final SalesReferenceService salesReferenceService;
    private final LeadCustomerConversionService leadCustomerConversionService;
    private final DriverRepository driverRepository;
    private final AuditEventService auditEventService;
    private final CurrentTenant currentTenant;
    private final JdbcClient jdbcClient;
    private final AssignmentService assignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            BookingStatus status,
            UUID customerId,
            UUID driverId,
            LocalDate from,
            LocalDate to,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Booking> specification = Specification
                .where(SearchSpecificationBuilder.<Booking>textContains(
                        search,
                        Set.of("bookingNumber", "notes")
                ))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("customerId", customerId))
                .and(SearchSpecificationBuilder.uuidEquals("driver.id", driverId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> from == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("travelStart"), from))
                .and((root, query, cb) -> to == null
                        ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.get("travelEnd"), to));
        return PageResponse.from(bookingRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(UUID id) {
        return toResponse(getBooking(id));
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING", action = "CREATE")
    public BookingResponse create(BookingRequest request) {
        UUID tenantId = currentTenant.id();
        String idempotencyKey = trimToNull(request.idempotencyKey());
        if (idempotencyKey != null) {
            Booking existing = bookingRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }
        validateCustomer(request.customerId(), tenantId);
        validateQuotation(request.quotationId(), tenantId);
        Quotation quotation = request.quotationId() == null
                ? null
                : quotationRepository.findByIdAndTenantId(request.quotationId(), tenantId).orElseThrow();
        validateDates(request.travelStart(), request.travelEnd());
        Driver driver = request.driverId() == null ? null : getDriver(request.driverId(), tenantId);
        BigDecimal agreedValue = money(request.agreedValue());
        UUID ownerId = currentTenant.userId();
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .tenantId(tenantId)
                .customerId(request.customerId())
                .quotationId(request.quotationId())
                .leadId(quotation == null ? null : quotation.getLeadId())
                .assignedUserId(ownerId)
                .driver(driver)
                .bookingNumber(nextNumber(tenantId))
                .travelStart(request.travelStart())
                .travelEnd(request.travelEnd())
                .status(request.status() == null ? BookingStatus.CONFIRMED : request.status())
                .agreedValue(agreedValue)
                .idempotencyKey(idempotencyKey)
                .notes(trimToNull(request.notes()))
                .active(request.active() == null || request.active())
                .build());
        publishOwnership(
                booking,
                quotation == null ? null : quotation.getAssignedUserId(),
                ownerId,
                "BOOKING_CREATED"
        );
        return toResponse(bookingRepository.save(booking));
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING", action = "CONVERT_QUOTATION")
    public BookingResponse convertQuotation(UUID quotationId, BookingConversionRequest request) {
        UUID tenantId = currentTenant.id();
        String idempotencyKey = request == null ? null : trimToNull(request.idempotencyKey());
        if (idempotencyKey != null) {
            Booking replay = bookingRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .orElse(null);
            if (replay != null) {
                if (!quotationId.equals(replay.getQuotationId())) {
                    throw new ConflictException("Idempotency key belongs to another booking");
                }
                return toResponse(replay);
            }
        }
        Booking existing = bookingRepository.findByTenantIdAndQuotationId(tenantId, quotationId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        Quotation quotation = quotationRepository.findByIdAndTenantId(quotationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new ConflictException("Only accepted quotations can be converted");
        }
        if (quotation.isSuperseded()) {
            throw new ConflictException("Superseded quotation revision cannot be converted");
        }
        Customer customer = leadCustomerConversionService.resolveForBooking(quotation);
        UUID ownerId = currentTenant.userId();
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .tenantId(tenantId)
                .customerId(customer.getId())
                .quotationId(quotation.getId())
                .leadId(quotation.getLeadId())
                .assignedUserId(ownerId)
                .bookingNumber(nextNumber(tenantId))
                .travelStart(quotation.getTravelStart())
                .travelEnd(quotation.getTravelEnd())
                .status(BookingStatus.CONFIRMED)
                .agreedValue(money(quotation.getTotal()))
                .idempotencyKey(idempotencyKey)
                .notes(request == null ? null : trimToNull(request.notes()))
                .active(true)
                .build());
        eventPublisher.publishEvent(resourceEvent(quotation, booking));
        publishOwnership(booking, quotation.getAssignedUserId(), ownerId, "BOOKING_CREATED");
        return toResponse(booking);
    }

    private BookingResourcesRequestedEvent resourceEvent(Quotation quotation, Booking booking) {
        List<QuotationItem> items =
                quotationItemRepository.findByQuotationIdAndActiveTrueOrderByPosition(quotation.getId());
        List<BookingResourcesRequestedEvent.RoomBlock> rooms = items.stream()
                .filter(item -> item.getType() == com.rakesh.zentrodesk.sales.quotation.QuotationItemType.HOTEL)
                .flatMap(item -> resources(item).stream().map(resource ->
                        new BookingResourcesRequestedEvent.RoomBlock(
                                item.getId(),
                                resource.getResourceId(),
                                item.getCheckIn(),
                                item.getCheckOut(),
                                item.isCapacityOverridden()
                        )))
                .toList();
        List<BookingResourcesRequestedEvent.TransportBlock> transports = items.stream()
                .filter(item -> item.getType() == com.rakesh.zentrodesk.sales.quotation.QuotationItemType.VEHICLE)
                .flatMap(item -> resources(item).stream().map(resource ->
                        new BookingResourcesRequestedEvent.TransportBlock(
                                item.getId(),
                                resource.getResourceId(),
                                item.getStartsAt(),
                                item.getEndsAt(),
                                item.getPickupLocation(),
                                item.getDropoffLocation(),
                                item.isCapacityOverridden()
                        )))
                .toList();
        List<BookingResourcesRequestedEvent.GuideBlock> guides = items.stream()
                .filter(item -> item.getType() == com.rakesh.zentrodesk.sales.quotation.QuotationItemType.GUIDE)
                .flatMap(item -> resources(item).stream().map(resource ->
                        new BookingResourcesRequestedEvent.GuideBlock(
                                resource.getResourceId(),
                                item.getStartsAt(),
                                item.getEndsAt()
                        )))
                .toList();
        return new BookingResourcesRequestedEvent(
                quotation.getTenantId(),
                booking.getId(),
                quotation.getId(),
                quotation.getAdults(),
                rooms,
                transports,
                guides
        );
    }

    private List<QuotationItemResource> resources(QuotationItem item) {
        return quotationItemResourceRepository
                .findByQuotationItemIdAndActiveTrueOrderByCreatedAt(item.getId());
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING", action = "UPDATE")
    public BookingResponse update(UUID id, BookingRequest request) {
        Booking booking = getBooking(id);
        assertVersion(booking.getVersion(), request.version(), "Booking");
        validateCustomer(request.customerId(), booking.getTenantId());
        validateQuotation(request.quotationId(), booking.getTenantId());
        validateDates(request.travelStart(), request.travelEnd());
        BigDecimal agreedValue = money(request.agreedValue());
        BigDecimal paid = paidAmount(booking.getId());
        if (agreedValue.compareTo(paid) < 0) {
            throw new ConflictException("Agreed value cannot be lower than recorded payments");
        }
        booking.setCustomerId(request.customerId());
        booking.setQuotationId(request.quotationId());
        booking.setDriver(request.driverId() == null
                ? null
                : getDriver(request.driverId(), booking.getTenantId()));
        booking.setTravelStart(request.travelStart());
        booking.setTravelEnd(request.travelEnd());
        booking.setAgreedValue(agreedValue);
        booking.setNotes(trimToNull(request.notes()));
        if (request.active() != null) {
            booking.setActive(request.active());
        }
        return toResponse(booking);
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING", action = "ASSIGN")
    public BookingResponse assign(UUID id, BookingAssignRequest request) {
        Booking booking = getBooking(id);
        assertVersion(booking.getVersion(), request.version(), "Booking");
        validateAssignedUser(request.assignedUserId(), booking.getTenantId());
        publishOwnership(
                booking,
                booking.getAssignedUserId(),
                request.assignedUserId(),
                "OWNER_REASSIGNED"
        );
        return toResponse(booking);
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING", action = "STATUS_UPDATE")
    public BookingResponse updateStatus(UUID id, BookingStatusRequest request) {
        Booking booking = getBooking(id);
        assertVersion(booking.getVersion(), request.version(), "Booking");
        if (booking.getStatus() == request.status()) {
            return toResponse(booking);
        }
        if (!BOOKING_TRANSITIONS.getOrDefault(booking.getStatus(), Set.of()).contains(request.status())) {
            throw new BadRequestException(
                    "Booking cannot transition from " + booking.getStatus() + " to " + request.status()
            );
        }
        booking.setStatus(request.status());
        if (request.status() == BookingStatus.CANCELLED) {
            assignmentService.cancelForBooking(booking.getId(), "Booking cancelled");
        }
        return toResponse(booking);
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING", action = "DISABLE")
    public void delete(UUID id) {
        Booking booking = getBooking(id);
        if (booking.getStatus() == BookingStatus.IN_PROGRESS) {
            throw new ConflictException("An in-progress booking cannot be disabled");
        }
        booking.setActive(false);
    }

    @Transactional(readOnly = true)
    public List<BookingGuestResponse> findGuests(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        return guestRepository.findByBookingIdAndActiveTrueOrderByPrimaryGuestDescCreatedAtAsc(booking.getId())
                .stream()
                .map(this::toGuestResponse)
                .toList();
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING_GUEST", action = "CREATE")
    public BookingGuestResponse createGuest(UUID bookingId, BookingGuestRequest request) {
        Booking booking = getBooking(bookingId);
        if (Boolean.TRUE.equals(request.primaryGuest())) {
            clearPrimaryGuest(booking.getId());
        }
        BookingGuest guest = guestRepository.save(BookingGuest.builder()
                .booking(booking)
                .name(request.name().trim())
                .gender(normalizedGender(request.gender()))
                .dateOfBirth(request.dateOfBirth())
                .phone(trimToNull(request.phone()))
                .identityType(trimToNull(request.identityType()))
                .identityNumber(trimToNull(request.identityNumber()))
                .primaryGuest(Boolean.TRUE.equals(request.primaryGuest()))
                .active(true)
                .build());
        return toGuestResponse(guest);
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING_GUEST", action = "UPDATE")
    public BookingGuestResponse updateGuest(UUID id, BookingGuestRequest request) {
        BookingGuest guest = getGuest(id);
        if (Boolean.TRUE.equals(request.primaryGuest())) {
            clearPrimaryGuest(guest.getBooking().getId());
        }
        guest.setName(request.name().trim());
        guest.setGender(normalizedGender(request.gender()));
        guest.setDateOfBirth(request.dateOfBirth());
        guest.setPhone(trimToNull(request.phone()));
        guest.setIdentityType(trimToNull(request.identityType()));
        guest.setIdentityNumber(trimToNull(request.identityNumber()));
        guest.setPrimaryGuest(Boolean.TRUE.equals(request.primaryGuest()));
        return toGuestResponse(guest);
    }

    @Transactional
    @AuditMutation(entityType = "BOOKING_GUEST", action = "DISABLE")
    public void disableGuest(UUID id) {
        getGuest(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findPayments(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        return paymentRepository.findByBookingIdAndActiveTrueOrderByCreatedAtDesc(booking.getId())
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Transactional
    @AuditMutation(entityType = "PAYMENT", action = "CREATE")
    public PaymentResponse createPayment(UUID bookingId, PaymentRequest request) {
        UUID tenantId = currentTenant.id();
        String idempotencyKey = trimToNull(request.idempotencyKey());
        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        Payment replay = paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElse(null);
        if (replay != null) {
            if (!bookingId.equals(replay.getBooking().getId())) {
                throw new ConflictException("Idempotency key belongs to another payment");
            }
            return toPaymentResponse(replay);
        }
        Booking booking = bookingRepository.findByIdAndTenantIdForUpdate(bookingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        PaymentStatus status = request.status() == null ? PaymentStatus.PAID : request.status();
        if (PAID_STATUSES.contains(status)) {
            ensureNoOverpayment(booking, request.amount());
        }
        Payment payment = Payment.builder()
                .tenantId(tenantId)
                .booking(booking)
                .amount(money(request.amount()))
                .method(request.method())
                .status(status)
                .transactionReference(trimToNull(request.transactionReference()))
                .idempotencyKey(idempotencyKey)
                .paidAt(request.paidAt())
                .remarks(trimToNull(request.remarks()))
                .active(request.active() == null || request.active())
                .build();
        Payment saved = paymentRepository.saveAndFlush(payment);
        publishPayment(saved);
        return toPaymentResponse(saved);
    }

    @Transactional
    @AuditMutation(entityType = "PAYMENT", action = "STATUS_UPDATE")
    public PaymentResponse updatePaymentStatus(UUID id, PaymentStatusRequest request) {
        Payment payment = getPayment(id);
        assertVersion(payment.getVersion(), request.version(), "Payment");
        if (payment.getStatus() == request.status()) {
            return toPaymentResponse(payment);
        }
        if (!PAYMENT_TRANSITIONS.getOrDefault(payment.getStatus(), Set.of()).contains(request.status())) {
            throw new BadRequestException(
                    "Payment cannot transition from " + payment.getStatus() + " to " + request.status()
            );
        }
        if (PAID_STATUSES.contains(request.status()) && !PAID_STATUSES.contains(payment.getStatus())) {
            Booking booking = bookingRepository.findByIdAndTenantIdForUpdate(
                            payment.getBooking().getId(),
                            currentTenant.id()
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            ensureNoOverpayment(booking, payment.getAmount());
        }
        payment.setStatus(request.status());
        paymentRepository.flush();
        publishPayment(payment);
        return toPaymentResponse(payment);
    }

    @Transactional
    @AuditMutation(entityType = "PAYMENT", action = "REVERSE")
    public PaymentResponse reversePayment(UUID id, PaymentReversalRequest request) {
        UUID tenantId = currentTenant.id();
        Payment replay = paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey())
                .orElse(null);
        if (replay != null) {
            return toPaymentResponse(replay);
        }
        Payment original = getPayment(id);
        if (!PAID_STATUSES.contains(original.getStatus())) {
            throw new ConflictException("Only paid payments can be reversed");
        }
        bookingRepository.findByIdAndTenantIdForUpdate(original.getBooking().getId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        original.setStatus(PaymentStatus.REFUNDED);
        Payment reversal = Payment.builder()
                .tenantId(tenantId)
                .booking(original.getBooking())
                .amount(original.getAmount())
                .method(original.getMethod())
                .status(PaymentStatus.REFUNDED)
                .idempotencyKey(request.idempotencyKey())
                .reversalOf(original)
                .paidAt(java.time.Instant.now())
                .remarks(request.reason().trim())
                .active(true)
                .build();
        Payment saved = paymentRepository.save(reversal);
        publishPayment(saved);
        return toPaymentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> timeline(UUID bookingId, int page, int size) {
        getBooking(bookingId);
        return auditEventService.findAll(
                page,
                size,
                "occurredAt",
                "DESC",
                "BOOKING",
                bookingId,
                null,
                null,
                null
        );
    }

    private Booking getBooking(UUID id) {
        return bookingRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private BookingGuest getGuest(UUID id) {
        return guestRepository.findByIdAndBookingTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Booking guest not found"));
    }

    private Payment getPayment(UUID id) {
        return paymentRepository.findByIdAndBookingTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private void publishPayment(Payment payment) {
        eventPublisher.publishEvent(new PaymentRecordedEvent(
                payment.getTenantId(),
                payment.getId(),
                payment.getBooking().getId(),
                payment.getBooking().getBookingNumber(),
                payment.getAmount(),
                payment.getStatus()
        ));
    }

    private void validateCustomer(UUID customerId, UUID tenantId) {
        salesReferenceService.requireCustomer(customerId, tenantId);
    }

    private void validateQuotation(UUID quotationId, UUID tenantId) {
        salesReferenceService.requireQuotation(quotationId, tenantId);
    }

    private void validateAssignedUser(UUID userId, UUID tenantId) {
        if (!userRepository.existsByIdAndTenantId(userId, tenantId)) {
            throw new ResourceNotFoundException("Assigned user not found");
        }
    }

    private void publishOwnership(
            Booking booking,
            UUID previousOwnerId,
            UUID ownerId,
            String reason
    ) {
        eventPublisher.publishEvent(new OwnershipChangedEvent(
                booking.getTenantId(),
                previousOwnerId,
                ownerId,
                currentTenant.userId(),
                reason,
                "BOOKING",
                booking.getId(),
                booking.getLeadId(),
                booking.getQuotationId(),
                booking.getId(),
                null,
                booking.getCustomerId(),
                Instant.now()
        ));
    }

    private Driver getDriver(UUID driverId, UUID tenantId) {
        return driverRepository.findByIdAndTenantId(driverId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BadRequestException("Travel end must be after travel start");
        }
    }

    private void ensureNoOverpayment(Booking booking, BigDecimal amount) {
        if (booking.getAgreedValue() == null || booking.getAgreedValue().signum() == 0) {
            return;
        }
        BigDecimal projected = paidAmount(booking.getId()).add(money(amount));
        if (projected.compareTo(booking.getAgreedValue()) > 0) {
            throw new ConflictException("Payment would exceed the booking balance");
        }
    }

    private BigDecimal paidAmount(UUID bookingId) {
        return money(paymentRepository.sumActiveByBookingAndStatuses(bookingId, PAID_STATUSES));
    }

    private void clearPrimaryGuest(UUID bookingId) {
        guestRepository.findByBookingIdAndActiveTrueOrderByPrimaryGuestDescCreatedAtAsc(bookingId)
                .forEach(guest -> guest.setPrimaryGuest(false));
    }

    private String normalizedGender(String gender) {
        return gender == null || gender.isBlank() ? null : gender.trim().toUpperCase();
    }

    private String nextNumber(UUID tenantId) {
        Long value = jdbcClient.sql("""
                        insert into zentro_desk.document_counters as counter (tenant_id, document_type, next_value)
                        values (:tenantId, 'BOOKING', 2)
                        on conflict (tenant_id, document_type)
                        do update set next_value = counter.next_value + 1
                        returning next_value - 1
                        """)
                .param("tenantId", tenantId)
                .query(Long.class)
                .single();
        return "B-" + LocalDate.now().getYear() + "-" + String.format("%06d", value);
    }

    private BookingResponse toResponse(Booking booking) {
        BigDecimal agreedValue = money(booking.getAgreedValue());
        BigDecimal paid = paidAmount(booking.getId());
        BigDecimal balance = agreedValue.subtract(paid).max(BigDecimal.ZERO).setScale(2);
        PaymentStatus paymentStatus = paid.signum() == 0
                ? PaymentStatus.PENDING
                : paid.compareTo(agreedValue) >= 0 && agreedValue.signum() > 0
                        ? PaymentStatus.PAID
                        : PaymentStatus.PARTIAL;
        return new BookingResponse(
                booking.getId(),
                booking.getCustomerId(),
                booking.getQuotationId(),
                booking.getLeadId(),
                booking.getAssignedUserId(),
                ownerName(booking.getAssignedUserId(), booking.getTenantId()),
                booking.getDriver() == null ? null : booking.getDriver().getId(),
                booking.getBookingNumber(),
                booking.getTravelStart(),
                booking.getTravelEnd(),
                booking.getStatus(),
                booking.getNotes(),
                booking.isActive(),
                agreedValue,
                paid,
                balance,
                paymentStatus,
                booking.getVersion(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }

    private BookingGuestResponse toGuestResponse(BookingGuest guest) {
        return new BookingGuestResponse(
                guest.getId(),
                guest.getName(),
                guest.getGender(),
                guest.getDateOfBirth(),
                guest.getPhone(),
                guest.getIdentityType(),
                guest.getIdentityNumber(),
                guest.isPrimaryGuest()
        );
    }

    private String ownerName(UUID ownerId, UUID tenantId) {
        if (ownerId == null) {
            return null;
        }
        return userRepository.findWithTenantRoleAndPermissionsByIdAndTenantId(ownerId, tenantId)
                .map(user -> (user.getFirstName() + " "
                        + (user.getLastName() == null ? "" : user.getLastName())).trim())
                .orElse(null);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getTransactionReference(),
                payment.getPaidAt(),
                payment.getRemarks(),
                payment.isActive(),
                payment.getIdempotencyKey(),
                payment.getReversalOf() == null ? null : payment.getReversalOf().getId(),
                payment.getVersion(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private void assertVersion(long current, Long expected, String resource) {
        if (expected != null && expected != current) {
            throw new ConflictException(resource + " was updated by another user");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
