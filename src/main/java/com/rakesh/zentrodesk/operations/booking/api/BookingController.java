package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.platform.audit.AuditEventResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/bookings")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_READ + "')")
    public ApiResponse<PageResponse<BookingResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(defaultValue = "createdAt") String sort,
                                                              @RequestParam(defaultValue = "DESC") String direction,
                                                              @RequestParam(required = false) String search,
                                                              @RequestParam(required = false) BookingStatus status,
                                                              @RequestParam(required = false) UUID customerId,
                                                              @RequestParam(required = false) UUID driverId,
                                                              @RequestParam(required = false) LocalDate from,
                                                              @RequestParam(required = false) LocalDate to,
                                                              @RequestParam(required = false) Boolean active,
                                                              HttpServletRequest servletRequest) {
        return ApiResponse.success("Bookings retrieved successfully", bookingService.findAll(page, size, sort, direction, search, status, customerId, driverId, from, to, active), servletRequest.getRequestURI());
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_READ + "')")
    public ApiResponse<BookingResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking retrieved successfully", bookingService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_CREATE + "')")
    public ApiResponse<BookingResponse> create(@Valid @RequestBody BookingRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking created successfully", bookingService.create(request), servletRequest.getRequestURI());
    }

    @PostMapping("/quotations/{id}/booking")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_CREATE + "')")
    public ApiResponse<BookingResponse> convertQuotation(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) BookingConversionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation converted successfully",
                bookingService.convertQuotation(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_UPDATE + "')")
    public ApiResponse<BookingResponse> update(@PathVariable UUID id, @Valid @RequestBody BookingRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking updated successfully", bookingService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/bookings/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_UPDATE + "')")
    public ApiResponse<BookingResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody BookingStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking status updated successfully", bookingService.updateStatus(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/bookings/{id}/assignee")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<BookingResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody BookingAssignRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Booking assigned successfully",
                bookingService.assign(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_DELETE + "')")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
        bookingService.delete(id);
        return ApiResponse.success("Booking disabled successfully", null, servletRequest.getRequestURI());
    }

    @GetMapping("/bookings/{id}/guests")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_READ + "')")
    public ApiResponse<List<BookingGuestResponse>> findGuests(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Booking guests retrieved successfully",
                bookingService.findGuests(id),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/bookings/{id}/guests")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_UPDATE + "')")
    public ApiResponse<BookingGuestResponse> createGuest(
            @PathVariable UUID id,
            @Valid @RequestBody BookingGuestRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Booking guest created successfully",
                bookingService.createGuest(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/booking-guests/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_UPDATE + "')")
    public ApiResponse<BookingGuestResponse> updateGuest(
            @PathVariable UUID id,
            @Valid @RequestBody BookingGuestRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Booking guest updated successfully",
                bookingService.updateGuest(id, request),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping("/booking-guests/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_UPDATE + "')")
    public ApiResponse<Void> disableGuest(@PathVariable UUID id, HttpServletRequest servletRequest) {
        bookingService.disableGuest(id);
        return ApiResponse.success("Booking guest disabled successfully", null, servletRequest.getRequestURI());
    }

    @GetMapping("/bookings/{id}/timeline")
    @PreAuthorize("hasAuthority('" + TenantPermissions.BOOKING_READ + "')")
    public ApiResponse<PageResponse<AuditEventResponse>> timeline(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Booking timeline retrieved successfully",
                bookingService.timeline(id, page, size),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/bookings/{id}/payments")
    @PreAuthorize("hasAuthority('" + TenantPermissions.PAYMENT_READ + "')")
    public ApiResponse<List<PaymentResponse>> findPayments(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Payments retrieved successfully", bookingService.findPayments(id), servletRequest.getRequestURI());
    }

    @PostMapping("/bookings/{id}/payments")
    @PreAuthorize("hasAuthority('" + TenantPermissions.PAYMENT_CREATE + "')")
    public ApiResponse<PaymentResponse> createPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Payment created successfully", bookingService.createPayment(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/payments/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.PAYMENT_UPDATE + "')")
    public ApiResponse<PaymentResponse> updatePaymentStatus(@PathVariable UUID id, @Valid @RequestBody PaymentStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Payment status updated successfully", bookingService.updatePaymentStatus(id, request), servletRequest.getRequestURI());
    }

    @PostMapping("/payments/{id}/reversal")
    @PreAuthorize("hasAuthority('" + TenantPermissions.PAYMENT_UPDATE + "')")
    public ApiResponse<PaymentResponse> reversePayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentReversalRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Payment reversed successfully",
                bookingService.reversePayment(id, request),
                servletRequest.getRequestURI()
        );
    }
}
