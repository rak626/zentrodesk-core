package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/driver-assignments")
@PreAuthorize("hasAuthority('" + TenantPermissions.DRIVER_READ + "')")
public class AssignmentController {

    private static final String MUTATE =
            "hasAuthority('" + TenantPermissions.DRIVER_UPDATE + "')"
                    + " and hasAuthority('" + TenantPermissions.BOOKING_UPDATE + "')";

    private final AssignmentService assignmentService;

    @GetMapping
    public ApiResponse<PageResponse<AssignmentResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "pickupAt") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assignments retrieved successfully",
                assignmentService.findAll(
                        page,
                        size,
                        sort,
                        direction,
                        bookingId,
                        driverId,
                        vehicleId,
                        status,
                        from,
                        to,
                        active
                ),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<AssignmentResponse> findById(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assignment retrieved successfully",
                assignmentService.findById(id),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/{id}/history")
    public ApiResponse<PageResponse<AssignmentHistoryResponse>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assignment history retrieved successfully",
                assignmentService.history(id, page, size),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/availability")
    public ApiResponse<TransportAvailabilityResponse> availability(
            @RequestParam Instant from,
            @RequestParam Instant to,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Transport availability retrieved successfully",
                assignmentService.availability(from, to),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping
    @PreAuthorize(MUTATE)
    public ApiResponse<AssignmentResponse> assign(
            @Valid @RequestBody AssignmentRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assigned successfully",
                assignmentService.assign(request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize(MUTATE)
    public ApiResponse<AssignmentResponse> reassign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentReassignRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assignment updated successfully",
                assignmentService.reassign(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(MUTATE)
    public ApiResponse<AssignmentResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentTransitionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assignment confirmed successfully",
                assignmentService.confirm(id, request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MUTATE)
    public ApiResponse<AssignmentResponse> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentTransitionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Driver assignment cancelled successfully",
                assignmentService.cancel(id, request),
                servletRequest.getRequestURI()
        );
    }
}
