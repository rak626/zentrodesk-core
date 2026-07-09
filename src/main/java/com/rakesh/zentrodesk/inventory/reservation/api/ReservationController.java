package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public ApiResponse<PageResponse<ReservationResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size,
                                                                  @RequestParam(defaultValue = "createdAt") String sort,
                                                                  @RequestParam(defaultValue = "DESC") String direction,
                                                                  @RequestParam(required = false) UUID bookingId,
                                                                  @RequestParam(required = false) UUID propertyId,
                                                                  @RequestParam(required = false) UUID roomId,
                                                                  @RequestParam(required = false) ReservationStatus status,
                                                                  @RequestParam(required = false) LocalDate from,
                                                                  @RequestParam(required = false) LocalDate to,
                                                                  HttpServletRequest servletRequest) {
        return ApiResponse.success("Reservations retrieved successfully", reservationService.findAll(page, size, sort, direction, bookingId, propertyId, roomId, status, from, to), servletRequest.getRequestURI());
    }

    @PostMapping
    public ApiResponse<ReservationResponse> create(@Valid @RequestBody ReservationRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Reservation created successfully", reservationService.create(request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ReservationResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody ReservationStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Reservation status updated successfully", reservationService.updateStatus(id, request), servletRequest.getRequestURI());
    }
}
