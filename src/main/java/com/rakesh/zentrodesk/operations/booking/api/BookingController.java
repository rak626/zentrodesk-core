package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/bookings")
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
    public ApiResponse<BookingResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking retrieved successfully", bookingService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping("/bookings")
    public ApiResponse<BookingResponse> create(@Valid @RequestBody BookingRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking created successfully", bookingService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/bookings/{id}")
    public ApiResponse<BookingResponse> update(@PathVariable UUID id, @Valid @RequestBody BookingRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking updated successfully", bookingService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/bookings/{id}/status")
    public ApiResponse<BookingResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody BookingStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Booking status updated successfully", bookingService.updateStatus(id, request), servletRequest.getRequestURI());
    }

    @DeleteMapping("/bookings/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
        bookingService.delete(id);
        return ApiResponse.success("Booking disabled successfully", null, servletRequest.getRequestURI());
    }

    @GetMapping("/bookings/{id}/payments")
    public ApiResponse<List<PaymentResponse>> findPayments(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Payments retrieved successfully", bookingService.findPayments(id), servletRequest.getRequestURI());
    }

    @PostMapping("/bookings/{id}/payments")
    public ApiResponse<PaymentResponse> createPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Payment created successfully", bookingService.createPayment(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/payments/{id}/status")
    public ApiResponse<PaymentResponse> updatePaymentStatus(@PathVariable UUID id, @Valid @RequestBody PaymentStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Payment status updated successfully", bookingService.updatePaymentStatus(id, request), servletRequest.getRequestURI());
    }
}
