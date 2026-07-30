package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/reservations")
@PreAuthorize("hasAuthority('" + TenantPermissions.RESERVATION_READ + "')")
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

    @GetMapping("/availability")
    public ApiResponse<AvailabilityResponse> availability(@RequestParam(required = false) LocalDate date,
                                                          @RequestParam(defaultValue = "WEEK") AvailabilityView view,
                                                          @RequestParam(required = false) UUID propertyId,
                                                          @RequestParam(required = false) Integer minCapacity,
                                                          HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "Availability retrieved successfully",
                reservationService.availability(date, view, propertyId, minCapacity),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/available-rooms")
    public ApiResponse<List<AvailableRoomResponse>> availableRooms(
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) Integer minCapacity,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Available rooms retrieved successfully",
                reservationService.availableRooms(checkIn, checkOut, propertyId, minCapacity),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.RESERVATION_CREATE + "')")
    public ApiResponse<ReservationResponse> create(@Valid @RequestBody ReservationRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Reservation created successfully", reservationService.create(request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.RESERVATION_UPDATE + "')")
    public ApiResponse<ReservationResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody ReservationStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Reservation status updated successfully", reservationService.updateStatus(id, request), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('" + TenantPermissions.RESERVATION_UPDATE + "')")
    public ApiResponse<ReservationResponse> release(@PathVariable UUID id,
                                                    @RequestBody ReservationReleaseRequest request,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "Reservation released successfully",
                reservationService.release(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('" + TenantPermissions.RESERVATION_UPDATE + "')")
    public ApiResponse<ReservationResponse> transfer(@PathVariable UUID id,
                                                     @Valid @RequestBody ReservationTransferRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "Reservation transferred successfully",
                reservationService.transfer(id, request),
                servletRequest.getRequestURI()
        );
    }
}
