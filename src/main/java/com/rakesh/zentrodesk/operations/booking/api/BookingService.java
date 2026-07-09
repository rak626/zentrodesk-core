package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.booking.Payment;
import com.rakesh.zentrodesk.operations.booking.PaymentRepository;
import com.rakesh.zentrodesk.operations.booking.PaymentStatus;
import com.rakesh.zentrodesk.operations.driver.Driver;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "bookingNumber", "travelStart", "travelEnd", "status", "active");
    private static final List<PaymentStatus> PAID_STATUSES = List.of(PaymentStatus.PAID, PaymentStatus.PARTIAL);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findAll(int page, int size, String sort, String direction, String search,
                                                 BookingStatus status, UUID customerId, UUID driverId, LocalDate from,
                                                 LocalDate to, Boolean active) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Booking> specification = Specification
                .where(SearchSpecificationBuilder.<Booking>textContains(search, Set.of("bookingNumber", "notes")))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("customerId", customerId))
                .and(SearchSpecificationBuilder.uuidEquals("driver.id", driverId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("travelStart"), from))
                .and((root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("travelEnd"), to));
        return PageResponse.from(bookingRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(UUID id) {
        return toResponse(getBooking(id));
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        UUID tenantId = currentTenant.id();
        validateCustomer(request.customerId(), tenantId);
        Driver driver = request.driverId() == null ? null : getDriver(request.driverId(), tenantId);
        Booking booking = Booking.builder()
                .tenantId(tenantId)
                .customerId(request.customerId())
                .quotationId(request.quotationId())
                .driver(driver)
                .bookingNumber(request.bookingNumber().trim())
                .travelStart(request.travelStart())
                .travelEnd(request.travelEnd())
                .status(request.status() == null ? BookingStatus.CONFIRMED : request.status())
                .notes(trimToNull(request.notes()))
                .active(request.active() == null || request.active())
                .build();
        return toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse update(UUID id, BookingRequest request) {
        Booking booking = getBooking(id);
        validateCustomer(request.customerId(), booking.getTenantId());
        booking.setCustomerId(request.customerId());
        booking.setQuotationId(request.quotationId());
        booking.setDriver(request.driverId() == null ? null : getDriver(request.driverId(), booking.getTenantId()));
        booking.setBookingNumber(request.bookingNumber().trim());
        booking.setTravelStart(request.travelStart());
        booking.setTravelEnd(request.travelEnd());
        if (request.status() != null) {
            booking.setStatus(request.status());
        }
        booking.setNotes(trimToNull(request.notes()));
        if (request.active() != null) {
            booking.setActive(request.active());
        }
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse updateStatus(UUID id, BookingStatusRequest request) {
        Booking booking = getBooking(id);
        booking.setStatus(request.status());
        return toResponse(booking);
    }

    @Transactional
    public void delete(UUID id) {
        getBooking(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findPayments(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        return paymentRepository.findByBookingIdAndActiveTrueOrderByCreatedAtDesc(booking.getId()).stream().map(this::toPaymentResponse).toList();
    }

    @Transactional
    public PaymentResponse createPayment(UUID bookingId, PaymentRequest request) {
        Booking booking = getBooking(bookingId);
        Payment payment = Payment.builder()
                .booking(booking)
                .amount(request.amount())
                .method(request.method())
                .status(request.status() == null ? PaymentStatus.PENDING : request.status())
                .transactionReference(trimToNull(request.transactionReference()))
                .paidAt(request.paidAt())
                .remarks(trimToNull(request.remarks()))
                .active(request.active() == null || request.active())
                .build();
        return toPaymentResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse updatePaymentStatus(UUID id, PaymentStatusRequest request) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        currentTenant.require(payment.getBooking().getTenantId());
        payment.setStatus(request.status());
        return toPaymentResponse(payment);
    }

    private Booking getBooking(UUID id) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        currentTenant.require(booking.getTenantId());
        return booking;
    }

    private void validateCustomer(UUID customerId, UUID tenantId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        currentTenant.require(customer.getTenantId());
        currentTenant.require(tenantId);
    }

    private Driver getDriver(UUID driverId, UUID tenantId) {
        Driver driver = driverRepository.findById(driverId).orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        currentTenant.require(driver.getTenantId());
        currentTenant.require(tenantId);
        return driver;
    }

    private BookingResponse toResponse(Booking booking) {
        BigDecimal paid = paymentRepository.sumActiveByBookingAndStatuses(booking.getId(), PAID_STATUSES);
        PaymentStatus status = paid.signum() <= 0 ? PaymentStatus.PENDING : PaymentStatus.PARTIAL;
        return new BookingResponse(booking.getId(), booking.getCustomerId(), booking.getQuotationId(),
                booking.getDriver() == null ? null : booking.getDriver().getId(), booking.getBookingNumber(),
                booking.getTravelStart(), booking.getTravelEnd(), booking.getStatus(), booking.getNotes(), booking.isActive(),
                paid, paid, status, booking.getCreatedAt(), booking.getUpdatedAt());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBooking().getId(), payment.getAmount(), payment.getMethod(),
                payment.getStatus(), payment.getTransactionReference(), payment.getPaidAt(), payment.getRemarks(),
                payment.isActive(), payment.getCreatedAt(), payment.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
