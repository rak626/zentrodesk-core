package com.rakesh.zentrodesk.operations.assignment.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.assignment.AssignmentAction;
import com.rakesh.zentrodesk.operations.assignment.AssignmentNotificationService;
import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;
import com.rakesh.zentrodesk.operations.assignment.DriverAssignment;
import com.rakesh.zentrodesk.operations.assignment.DriverAssignmentHistory;
import com.rakesh.zentrodesk.operations.assignment.DriverAssignmentHistoryRepository;
import com.rakesh.zentrodesk.operations.assignment.DriverAssignmentRepository;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingResourcesRequestedEvent;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.operations.driver.Driver;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import com.rakesh.zentrodesk.operations.driver.DriverStatusHistory;
import com.rakesh.zentrodesk.operations.driver.DriverStatusHistoryRepository;
import com.rakesh.zentrodesk.operations.driver.Vehicle;
import com.rakesh.zentrodesk.operations.driver.VehicleRepository;
import com.rakesh.zentrodesk.operations.driver.VehicleStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "updatedAt", "pickupAt", "dropoffAt", "status");
    private static final Set<AssignmentStatus> OCCUPYING_STATUSES =
            Set.of(AssignmentStatus.PENDING, AssignmentStatus.CONFIRMED);

    private final DriverAssignmentRepository assignmentRepository;
    private final DriverAssignmentHistoryRepository historyRepository;
    private final DriverStatusHistoryRepository driverHistoryRepository;
    private final BookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TenantRepository tenantRepository;
    private final AssignmentNotificationService notificationService;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            UUID bookingId,
            UUID driverId,
            UUID vehicleId,
            AssignmentStatus status,
            Instant from,
            Instant to,
            Boolean active
    ) {
        validateOptionalRange(from, to);
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<DriverAssignment> specification = Specification
                .where(SearchSpecificationBuilder.<DriverAssignment>uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("booking.id", bookingId))
                .and(SearchSpecificationBuilder.uuidEquals("driver.id", driverId))
                .and(SearchSpecificationBuilder.uuidEquals("vehicle.id", vehicleId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> from == null
                        ? cb.conjunction()
                        : cb.greaterThan(root.get("dropoffAt"), from))
                .and((root, query, cb) -> to == null
                        ? cb.conjunction()
                        : cb.lessThan(root.get("pickupAt"), to));
        boolean detailsVisible = canViewDetails();
        return PageResponse.from(assignmentRepository.findAll(specification, pageable)
                .map(assignment -> toResponse(assignment, detailsVisible)));
    }

    @Transactional(readOnly = true)
    public AssignmentResponse findById(UUID id) {
        return toResponse(getAssignment(id), canViewDetails());
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentHistoryResponse> history(UUID id, int page, int size) {
        DriverAssignment assignment = getAssignment(id);
        Pageable pageable = PaginationUtils.pageable(
                page,
                size,
                "createdAt",
                "DESC",
                Set.of("createdAt")
        );
        return PageResponse.from(historyRepository
                .findByTenantIdAndAssignmentId(currentTenant.id(), assignment.getId(), pageable)
                .map(this::toHistoryResponse));
    }

    @Transactional(readOnly = true)
    public TransportAvailabilityResponse availability(Instant from, Instant to) {
        validateRange(from, to, false);
        UUID tenantId = currentTenant.id();
        List<DriverAssignment> assignments =
                assignmentRepository.findOverlapping(tenantId, from, to, OCCUPYING_STATUSES);
        Set<UUID> occupiedDrivers = assignments.stream()
                .map(assignment -> assignment.getDriver().getId())
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> occupiedVehicles = assignments.stream()
                .map(assignment -> assignment.getVehicle().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<AvailableDriverResponse> drivers = driverRepository
                .findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)
                .stream()
                .map(driver -> new AvailableDriverResponse(
                        driver.getId(),
                        driver.getName(),
                        driver.getVehicle() == null ? null : driver.getVehicle().getId(),
                        driver.getStatus(),
                        driver.getStatus() != DriverStatus.UNAVAILABLE
                                && !occupiedDrivers.contains(driver.getId())
                ))
                .toList();
        List<AvailableVehicleResponse> vehicles = vehicleRepository
                .findByTenantIdAndActiveTrueOrderByRegistrationNumberAsc(tenantId)
                .stream()
                .map(vehicle -> new AvailableVehicleResponse(
                        vehicle.getId(),
                        vehicle.getRegistrationNumber(),
                        vehicle.getType(),
                        vehicle.getStatus(),
                        vehicle.getStatus() == VehicleStatus.AVAILABLE
                                && !occupiedVehicles.contains(vehicle.getId())
                ))
                .toList();
        return new TransportAvailabilityResponse(
                from,
                to,
                drivers,
                vehicles,
                assignments.stream().map(this::toWindowResponse).toList()
        );
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER_ASSIGNMENT", action = "ASSIGN")
    public AssignmentResponse assign(AssignmentRequest request) {
        UUID tenantId = currentTenant.id();
        DriverAssignment replay = assignmentRepository
                .findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey().trim())
                .orElse(null);
        if (replay != null) {
            if (!replay.getBooking().getId().equals(request.bookingId())) {
                throw new ConflictException("Idempotency key belongs to another assignment");
            }
            return toResponse(replay, true);
        }
        validateRange(request.pickupAt(), request.dropoffAt(), true);
        Booking booking = getBooking(request.bookingId());
        Driver driver = getAvailableDriver(request.driverId());
        Vehicle vehicle = getAvailableVehicle(request.vehicleId());
        validateBooking(booking, request.pickupAt(), request.dropoffAt());
        ensureAvailable(driver.getId(), vehicle.getId(), request.pickupAt(), request.dropoffAt(), null);
        DriverAssignment assignment = DriverAssignment.builder()
                .tenantId(tenantId)
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .pickupAt(request.pickupAt())
                .dropoffAt(request.dropoffAt())
                .pickupLocation(request.pickupLocation().trim())
                .dropoffLocation(request.dropoffLocation().trim())
                .status(AssignmentStatus.PENDING)
                .idempotencyKey(request.idempotencyKey().trim())
                .active(true)
                .build();
        assignment = save(assignment);
        booking.setDriver(driver);
        recordHistory(assignment, AssignmentAction.ASSIGNED, null, null, null, "Assignment created");
        notificationService.enqueue(assignment, AssignmentAction.ASSIGNED);
        return toResponse(assignment, true);
    }

    @EventListener
    public void assignQuotationTransport(BookingResourcesRequestedEvent event) {
        if (event.transports().isEmpty()) {
            return;
        }
        Booking booking = bookingRepository.findByIdAndTenantId(event.bookingId(), event.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        event.transports().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        BookingResourcesRequestedEvent.TransportBlock::quotationItemId
                ))
                .values()
                .forEach(blocks -> {
                    if (blocks.getFirst().capacityOverridden()) {
                        return;
                    }
                    int capacity = blocks.stream()
                            .mapToInt(block -> getAvailableVehicle(block.vehicleId()).getCapacity())
                            .sum();
                    if (capacity < event.adults()) {
                        throw new ConflictException(
                                "Selected vehicles hold " + capacity + " adults; " + event.adults()
                                        + " are required. Enable the capacity override to continue."
                        );
                    }
                });
        event.transports().forEach(block -> {
            Vehicle vehicle = getAvailableVehicle(block.vehicleId());
            Driver driver = driverRepository.findByTenantIdAndVehicleIdAndActiveTrue(
                            event.tenantId(),
                            vehicle.getId()
                    )
                    .orElseThrow(() -> new ConflictException("Selected vehicle has no fixed driver"));
            if (driver.getStatus() == DriverStatus.UNAVAILABLE) {
                throw new ConflictException("Selected vehicle driver is unavailable");
            }
            validateRange(block.pickupAt(), block.dropoffAt(), true);
            validateBooking(booking, block.pickupAt(), block.dropoffAt());
            ensureAvailable(driver.getId(), vehicle.getId(), block.pickupAt(), block.dropoffAt(), null);
            DriverAssignment assignment = save(DriverAssignment.builder()
                    .tenantId(event.tenantId())
                    .booking(booking)
                    .driver(driver)
                    .vehicle(vehicle)
                    .pickupAt(block.pickupAt())
                    .dropoffAt(block.dropoffAt())
                    .pickupLocation(block.pickupLocation())
                    .dropoffLocation(block.dropoffLocation())
                    .status(AssignmentStatus.PENDING)
                    .idempotencyKey(event.quotationId() + "-vehicle-" + vehicle.getId())
                    .active(true)
                    .build());
            if (booking.getDriver() == null) {
                booking.setDriver(driver);
            }
            recordHistory(assignment, AssignmentAction.ASSIGNED, null, null, null, "Created from quotation");
            notificationService.enqueue(assignment, AssignmentAction.ASSIGNED);
        });
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER_ASSIGNMENT", action = "REASSIGN")
    public AssignmentResponse reassign(UUID id, AssignmentReassignRequest request) {
        DriverAssignment assignment = getAssignmentForUpdate(id);
        assertMutable(assignment);
        assertVersion(assignment.getVersion(), request.version());
        Driver driver = getAvailableDriver(request.driverId());
        Vehicle vehicle = getAvailableVehicle(request.vehicleId());
        ensureAvailable(
                driver.getId(),
                vehicle.getId(),
                assignment.getPickupAt(),
                assignment.getDropoffAt(),
                assignment.getId()
        );
        Driver previousDriver = assignment.getDriver();
        Vehicle previousVehicle = assignment.getVehicle();
        assignment.setDriver(driver);
        assignment.setVehicle(vehicle);
        assignment = save(assignment);
        assignment.getBooking().setDriver(driver);
        if (assignment.getStatus() == AssignmentStatus.CONFIRMED) {
            releaseDriverIfUnused(previousDriver);
            markDriverOnTrip(driver, "Confirmed assignment reassigned");
        }
        recordHistory(
                assignment,
                AssignmentAction.REASSIGNED,
                previousDriver.getId(),
                previousVehicle.getId(),
                assignment.getStatus(),
                request.reason()
        );
        notificationService.enqueue(assignment, AssignmentAction.REASSIGNED);
        return toResponse(assignment, true);
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER_ASSIGNMENT", action = "CONFIRM")
    public AssignmentResponse confirm(UUID id, AssignmentTransitionRequest request) {
        DriverAssignment assignment = getAssignmentForUpdate(id);
        assertVersion(assignment.getVersion(), request.version());
        if (assignment.getStatus() == AssignmentStatus.CONFIRMED) {
            return toResponse(assignment, true);
        }
        assertMutable(assignment);
        ensureAvailable(
                assignment.getDriver().getId(),
                assignment.getVehicle().getId(),
                assignment.getPickupAt(),
                assignment.getDropoffAt(),
                assignment.getId()
        );
        AssignmentStatus previous = assignment.getStatus();
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment = save(assignment);
        markDriverOnTrip(assignment.getDriver(), "Assignment confirmed");
        recordHistory(
                assignment,
                AssignmentAction.CONFIRMED,
                null,
                null,
                previous,
                request.reason()
        );
        notificationService.enqueue(assignment, AssignmentAction.CONFIRMED);
        return toResponse(assignment, true);
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER_ASSIGNMENT", action = "CANCEL")
    public AssignmentResponse cancel(UUID id, AssignmentTransitionRequest request) {
        DriverAssignment assignment = getAssignmentForUpdate(id);
        if (assignment.getStatus() == AssignmentStatus.CANCELLED || !assignment.isActive()) {
            return toResponse(assignment, true);
        }
        assertVersion(assignment.getVersion(), request.version());
        cancelAssignment(assignment, request.reason());
        return toResponse(assignment, true);
    }

    @Transactional
    public void cancelForBooking(UUID bookingId, String reason) {
        UUID tenantId = currentTenant.id();
        assignmentRepository.findByTenantIdAndBookingIdAndActiveTrueAndStatusIn(
                        tenantId,
                        bookingId,
                        OCCUPYING_STATUSES
                )
                .forEach(assignment -> cancelAssignment(assignment, reason));
    }

    private void cancelAssignment(DriverAssignment assignment, String reason) {
        AssignmentStatus previous = assignment.getStatus();
        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setActive(false);
        assignmentRepository.saveAndFlush(assignment);
        if (assignment.getBooking().getDriver() != null
                && assignment.getBooking().getDriver().getId().equals(assignment.getDriver().getId())) {
            assignment.getBooking().setDriver(null);
        }
        recordHistory(
                assignment,
                AssignmentAction.CANCELLED,
                null,
                null,
                previous,
                reason
        );
        releaseDriverIfUnused(assignment.getDriver());
        notificationService.enqueue(assignment, AssignmentAction.CANCELLED);
    }

    private DriverAssignment save(DriverAssignment assignment) {
        try {
            return assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Driver or vehicle is unavailable for the selected time");
        }
    }

    private void ensureAvailable(
            UUID driverId,
            UUID vehicleId,
            Instant pickupAt,
            Instant dropoffAt,
            UUID excludedId
    ) {
        if (!assignmentRepository.findConflicts(
                currentTenant.id(),
                driverId,
                vehicleId,
                pickupAt,
                dropoffAt,
                excludedId,
                OCCUPYING_STATUSES
        ).isEmpty()) {
            throw new ConflictException("Driver or vehicle is unavailable for the selected time");
        }
    }

    private void validateRange(Instant pickupAt, Instant dropoffAt, boolean rejectPast) {
        if (!pickupAt.isBefore(dropoffAt)) {
            throw new BadRequestException("Drop-off must be after pickup");
        }
        if (rejectPast && pickupAt.isBefore(Instant.now())) {
            throw new BadRequestException("Pickup cannot be in the past");
        }
    }

    private void validateOptionalRange(Instant from, Instant to) {
        if (from != null && to != null) {
            validateRange(from, to, false);
        }
    }

    private void validateBooking(Booking booking, Instant pickupAt, Instant dropoffAt) {
        if (!booking.isActive()
                || booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Booking is not open for driver assignment");
        }
        ZoneId tenantZone = tenantZone(booking.getTenantId());
        if (booking.getTravelStart() != null
                && pickupAt.atZone(tenantZone).toLocalDate().isBefore(booking.getTravelStart())
                || booking.getTravelEnd() != null
                && dropoffAt.atZone(tenantZone).toLocalDate().isAfter(booking.getTravelEnd())) {
            throw new BadRequestException("Assignment times must be within the booking dates");
        }
    }

    private ZoneId tenantZone(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        try {
            return ZoneId.of(tenant.getTimezone());
        } catch (DateTimeException | NullPointerException exception) {
            throw new BadRequestException("Tenant timezone is invalid");
        }
    }

    private Booking getBooking(UUID id) {
        return bookingRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private Driver getAvailableDriver(UUID id) {
        Driver driver = driverRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        if (!driver.isActive() || driver.getStatus() == DriverStatus.UNAVAILABLE) {
            throw new ConflictException("Driver is unavailable");
        }
        return driver;
    }

    private Vehicle getAvailableVehicle(UUID id) {
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        if (!vehicle.isActive() || vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is unavailable");
        }
        return vehicle;
    }

    private DriverAssignment getAssignment(UUID id) {
        return assignmentRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Driver assignment not found"));
    }

    private DriverAssignment getAssignmentForUpdate(UUID id) {
        return assignmentRepository.findByIdAndTenantIdForUpdate(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Driver assignment not found"));
    }

    private void assertMutable(DriverAssignment assignment) {
        if (!assignment.isActive() || assignment.getStatus() == AssignmentStatus.CANCELLED) {
            throw new ConflictException("Cancelled assignments cannot be changed");
        }
    }

    private void assertVersion(long currentVersion, Long requestedVersion) {
        if (requestedVersion != null && currentVersion != requestedVersion) {
            throw new ConflictException("Driver assignment was updated by another request");
        }
    }

    private void markDriverOnTrip(Driver driver, String reason) {
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            return;
        }
        DriverStatus previous = driver.getStatus();
        driver.setStatus(DriverStatus.ON_TRIP);
        recordDriverStatus(driver, previous, DriverStatus.ON_TRIP, reason);
    }

    private void releaseDriverIfUnused(Driver driver) {
        boolean hasConfirmed = assignmentRepository
                .existsByTenantIdAndDriverIdAndActiveTrueAndStatusIn(
                        currentTenant.id(),
                        driver.getId(),
                        Set.of(AssignmentStatus.CONFIRMED)
                );
        if (driver.getStatus() == DriverStatus.ON_TRIP && !hasConfirmed) {
            driver.setStatus(DriverStatus.AVAILABLE);
            recordDriverStatus(
                    driver,
                    DriverStatus.ON_TRIP,
                    DriverStatus.AVAILABLE,
                    "No confirmed assignments remain"
            );
        }
    }

    private void recordDriverStatus(
            Driver driver,
            DriverStatus previousStatus,
            DriverStatus status,
            String reason
    ) {
        driverHistoryRepository.save(DriverStatusHistory.builder()
                .tenantId(driver.getTenantId())
                .driver(driver)
                .previousStatus(previousStatus)
                .status(status)
                .reason(reason)
                .changedBy(currentUserId())
                .build());
    }

    private void recordHistory(
            DriverAssignment assignment,
            AssignmentAction action,
            UUID previousDriverId,
            UUID previousVehicleId,
            AssignmentStatus previousStatus,
            String reason
    ) {
        historyRepository.save(DriverAssignmentHistory.builder()
                .tenantId(assignment.getTenantId())
                .assignment(assignment)
                .action(action)
                .previousDriverId(previousDriverId)
                .driverId(assignment.getDriver().getId())
                .previousVehicleId(previousVehicleId)
                .vehicleId(assignment.getVehicle().getId())
                .previousStatus(previousStatus)
                .status(assignment.getStatus())
                .reason(trimToNull(reason))
                .changedBy(currentUserId())
                .build());
    }

    private UUID currentUserId() {
        try {
            return currentTenant.userId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean canViewDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> TenantPermissions.DRIVER_UPDATE.equals(authority.getAuthority()));
    }

    private AssignmentResponse toResponse(DriverAssignment assignment, boolean detailsVisible) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getBooking().getId(),
                assignment.getBooking().getBookingNumber(),
                assignment.getDriver().getId(),
                assignment.getDriver().getName(),
                assignment.getVehicle().getId(),
                assignment.getVehicle().getRegistrationNumber(),
                assignment.getPickupAt(),
                assignment.getDropoffAt(),
                detailsVisible ? assignment.getPickupLocation() : null,
                detailsVisible ? assignment.getDropoffLocation() : null,
                assignment.getStatus(),
                assignment.isActive(),
                detailsVisible,
                assignment.getVersion(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }

    private AssignmentHistoryResponse toHistoryResponse(DriverAssignmentHistory history) {
        return new AssignmentHistoryResponse(
                history.getId(),
                history.getAction(),
                history.getPreviousDriverId(),
                history.getDriverId(),
                history.getPreviousVehicleId(),
                history.getVehicleId(),
                history.getPreviousStatus(),
                history.getStatus(),
                history.getReason(),
                history.getChangedBy(),
                history.getCreatedAt()
        );
    }

    private AssignmentWindowResponse toWindowResponse(DriverAssignment assignment) {
        return new AssignmentWindowResponse(
                assignment.getId(),
                assignment.getBooking().getId(),
                assignment.getDriver().getId(),
                assignment.getVehicle().getId(),
                assignment.getPickupAt(),
                assignment.getDropoffAt(),
                assignment.getStatus()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
