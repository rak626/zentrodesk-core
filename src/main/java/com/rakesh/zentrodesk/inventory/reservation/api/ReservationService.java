package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.inventory.property.Property;
import com.rakesh.zentrodesk.inventory.property.PropertyRepository;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.reservation.Reservation;
import com.rakesh.zentrodesk.inventory.reservation.ReservationRepository;
import com.rakesh.zentrodesk.inventory.reservation.ReservationChangedEvent;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.inventory.room.Room;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingResourcesRequestedEvent;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "checkIn", "checkOut", "status", "active");
    private static final List<ReservationStatus> OCCUPYING_STATUSES = List.of(
            ReservationStatus.RESERVED,
            ReservationStatus.CHECKED_IN
    );

    private final ReservationRepository reservationRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final TenantRepository tenantRepository;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            UUID bookingId,
            UUID propertyId,
            UUID roomId,
            ReservationStatus status,
            LocalDate from,
            LocalDate to
    ) {
        validateOptionalRange(from, to);
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Reservation> spec = Specification
                .where(SearchSpecificationBuilder.<Reservation>uuidEquals("property.tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("bookingId", bookingId))
                .and(SearchSpecificationBuilder.uuidEquals("property.id", propertyId))
                .and(SearchSpecificationBuilder.uuidEquals("room.id", roomId))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThan(root.get("checkOut"), from))
                .and((root, query, cb) -> to == null ? cb.conjunction() : cb.lessThan(root.get("checkIn"), to));
        return PageResponse.from(reservationRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(
            LocalDate date,
            AvailabilityView view,
            UUID propertyId,
            Integer minCapacity
    ) {
        UUID tenantId = currentTenant.id();
        if (propertyId != null) {
            getProperty(propertyId);
        }
        if (minCapacity != null && minCapacity < 1) {
            throw new BadRequestException("Minimum capacity must be at least one");
        }

        AvailabilityView resolvedView = view == null ? AvailabilityView.WEEK : view;
        LocalDate from = date == null ? currentTenantDate(tenantId) : date;
        LocalDate to = switch (resolvedView) {
            case DAY -> from.plusDays(1);
            case WEEK -> from.plusDays(7);
            case MONTH -> from.plusMonths(1);
        };

        Specification<Room> roomSpec = Specification
                .where(SearchSpecificationBuilder.<Room>uuidEquals("property.tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("property.id", propertyId))
                .and(SearchSpecificationBuilder.booleanEquals("active", true))
                .and((root, query, cb) -> cb.isTrue(root.get("property").get("active")))
                .and((root, query, cb) -> minCapacity == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
        List<Room> rooms = roomRepository.findAll(
                roomSpec,
                Sort.by(Sort.Order.asc("property.name"), Sort.Order.asc("name"))
        );
        Map<UUID, List<Reservation>> reservationsByRoom = reservationRepository.findActiveOverlapping(
                        tenantId,
                        from,
                        to,
                        propertyId,
                        minCapacity,
                        OCCUPYING_STATUSES
                ).stream()
                .collect(Collectors.groupingBy(reservation -> reservation.getRoom().getId()));

        List<RoomAvailabilityResponse> availability = rooms.stream()
                .map(room -> toAvailability(room, reservationsByRoom.getOrDefault(room.getId(), List.of())))
                .toList();
        return new AvailabilityResponse(
                resolvedView,
                from,
                to,
                from.datesUntil(to).toList(),
                availability
        );
    }

    @Transactional(readOnly = true)
    public List<AvailableRoomResponse> availableRooms(
            LocalDate checkIn,
            LocalDate checkOut,
            UUID propertyId,
            Integer minCapacity
    ) {
        validateDates(checkIn, checkOut, true);
        UUID tenantId = currentTenant.id();
        if (propertyId != null) {
            getProperty(propertyId);
        }
        if (minCapacity != null && minCapacity < 1) {
            throw new BadRequestException("Minimum capacity must be at least one");
        }
        Specification<Room> specification = Specification
                .where(SearchSpecificationBuilder.<Room>uuidEquals("property.tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("property.id", propertyId))
                .and(SearchSpecificationBuilder.booleanEquals("active", true))
                .and((root, query, cb) -> cb.isTrue(root.get("property").get("active")))
                .and((root, query, cb) -> cb.equal(root.get("status"), RoomStatus.AVAILABLE))
                .and((root, query, cb) -> cb.equal(root.get("property").get("status"), PropertyStatus.ACTIVE))
                .and((root, query, cb) -> minCapacity == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
        return roomRepository.findAll(
                        specification,
                        Sort.by(Sort.Order.asc("property.name"), Sort.Order.asc("name"))
                ).stream()
                .filter(room -> !reservationRepository.existsActiveOverlap(
                        room.getId(),
                        checkIn,
                        checkOut,
                        null,
                        OCCUPYING_STATUSES
                ))
                .map(room -> new AvailableRoomResponse(
                        room.getId(),
                        room.getProperty().getId(),
                        room.getProperty().getName(),
                        room.getName(),
                        room.getType(),
                        room.getCapacity(),
                        room.getBasePrice(),
                        room.getAmenitiesSummary()
                ))
                .toList();
    }

    @Transactional
    @AuditMutation(entityType = "RESERVATION", action = "CREATE")
    public ReservationResponse create(ReservationRequest request) {
        validateDates(request.checkIn(), request.checkOut(), true);
        if (request.status() != null && request.status() != ReservationStatus.RESERVED) {
            throw new BadRequestException("New reservations must start in RESERVED status");
        }
        if (Boolean.FALSE.equals(request.active())) {
            throw new BadRequestException("New reservations must be active");
        }

        Booking booking = getBooking(request.bookingId());
        Property property = getProperty(request.propertyId());
        Room room = getRoom(request.roomId());
        validateBooking(booking, request.checkIn(), request.checkOut());
        validateRoom(property, room);
        ensureAvailable(room.getId(), request.checkIn(), request.checkOut(), null);

        Reservation reservation = Reservation.builder()
                .bookingId(booking.getId())
                .property(property)
                .room(room)
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .status(ReservationStatus.RESERVED)
                .active(true)
                .build();
        return save(reservation);
    }

    @EventListener
    public void reserveQuotationRooms(BookingResourcesRequestedEvent event) {
        if (event.rooms().isEmpty()) {
            return;
        }
        Booking booking = bookingRepository.findByIdAndTenantId(event.bookingId(), event.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        event.rooms().stream()
                .collect(Collectors.groupingBy(BookingResourcesRequestedEvent.RoomBlock::quotationItemId))
                .values()
                .forEach(blocks -> {
                    if (blocks.getFirst().capacityOverridden()) {
                        return;
                    }
                    int capacity = blocks.stream()
                            .mapToInt(block -> roomRepository
                                    .findByIdAndPropertyTenantId(block.roomId(), event.tenantId())
                                    .orElseThrow(() -> new ResourceNotFoundException("Selected room not found"))
                                    .getCapacity())
                            .sum();
                    if (capacity < event.adults()) {
                        throw new ConflictException(
                                "Selected rooms hold " + capacity + " adults; " + event.adults()
                                        + " are required. Enable the capacity override to continue."
                        );
                    }
                });
        event.rooms().forEach(block -> {
            Room room = roomRepository.findByIdAndPropertyTenantId(block.roomId(), event.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Selected room not found"));
            validateDates(block.checkIn(), block.checkOut(), true);
            validateBooking(booking, block.checkIn(), block.checkOut());
            validateRoom(room.getProperty(), room);
            ensureAvailable(room.getId(), block.checkIn(), block.checkOut(), null);
            save(Reservation.builder()
                    .bookingId(booking.getId())
                    .property(room.getProperty())
                    .room(room)
                    .checkIn(block.checkIn())
                    .checkOut(block.checkOut())
                    .status(ReservationStatus.RESERVED)
                    .active(true)
                    .build());
        });
    }

    @Transactional
    @AuditMutation(entityType = "RESERVATION", action = "STATUS_UPDATE")
    public ReservationResponse updateStatus(UUID id, ReservationStatusRequest request) {
        Reservation reservation = getReservation(id);
        getBooking(reservation.getBookingId());
        if (reservation.getStatus() == request.status()) {
            return toResponse(reservation);
        }
        if (request.status() == ReservationStatus.CANCELLED) {
            return release(reservation, request.version()) ? save(reservation) : toResponse(reservation);
        }

        assertVersion(reservation.getVersion(), request.version());
        boolean validTransition = reservation.getStatus() == ReservationStatus.RESERVED
                && request.status() == ReservationStatus.CHECKED_IN
                || reservation.getStatus() == ReservationStatus.CHECKED_IN
                && request.status() == ReservationStatus.CHECKED_OUT;
        if (!validTransition) {
            throw new BadRequestException("Invalid reservation status transition");
        }
        reservation.setStatus(request.status());
        return save(reservation);
    }

    @Transactional
    @AuditMutation(entityType = "RESERVATION", action = "RELEASE")
    public ReservationResponse release(UUID id, ReservationReleaseRequest request) {
        Reservation reservation = getReservation(id);
        getBooking(reservation.getBookingId());
        return release(reservation, request.version()) ? save(reservation) : toResponse(reservation);
    }

    @Transactional
    @AuditMutation(entityType = "RESERVATION", action = "TRANSFER")
    public ReservationResponse transfer(UUID id, ReservationTransferRequest request) {
        Reservation reservation = getReservation(id);
        Booking booking = getBooking(reservation.getBookingId());
        if (reservation.getRoom().getId().equals(request.roomId())) {
            return toResponse(reservation);
        }
        assertVersion(reservation.getVersion(), request.version());
        if (!reservation.isActive()
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new BadRequestException("Only active reservations can be transferred");
        }

        Room room = getRoom(request.roomId());
        Property property = room.getProperty();
        validateBooking(booking, reservation.getCheckIn(), reservation.getCheckOut());
        validateRoom(property, room);
        ensureAvailable(room.getId(), reservation.getCheckIn(), reservation.getCheckOut(), reservation.getId());
        reservation.setProperty(property);
        reservation.setRoom(room);
        return save(reservation);
    }

    private boolean release(Reservation reservation, Long version) {
        if (!reservation.isActive() || reservation.getStatus() == ReservationStatus.CANCELLED) {
            return false;
        }
        assertVersion(reservation.getVersion(), version);
        if (reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new BadRequestException("Checked-out reservations cannot be released");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setActive(false);
        return true;
    }

    private ReservationResponse save(Reservation reservation) {
        try {
            Reservation saved = reservationRepository.saveAndFlush(reservation);
            eventPublisher.publishEvent(new ReservationChangedEvent(
                    saved.getProperty().getTenantId(),
                    saved.getId(),
                    saved.getBookingId(),
                    saved.getStatus()
            ));
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Room is unavailable for the selected dates");
        }
    }

    private void ensureAvailable(UUID roomId, LocalDate checkIn, LocalDate checkOut, UUID excludedId) {
        if (reservationRepository.existsActiveOverlap(
                roomId,
                checkIn,
                checkOut,
                excludedId,
                OCCUPYING_STATUSES
        )) {
            throw new ConflictException("Room is unavailable for the selected dates");
        }
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut, boolean rejectPast) {
        if (!checkIn.isBefore(checkOut)) {
            throw new BadRequestException("Check-out must be after check-in");
        }
        if (rejectPast && checkIn.isBefore(currentTenantDate(currentTenant.id()))) {
            throw new BadRequestException("Check-in cannot be in the past");
        }
    }

    private void validateOptionalRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("The end date must be after the start date");
        }
    }

    private void validateBooking(Booking booking, LocalDate checkIn, LocalDate checkOut) {
        if (!booking.isActive()
                || booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Booking is not open for reservations");
        }
        if (booking.getTravelStart() != null && checkIn.isBefore(booking.getTravelStart())
                || booking.getTravelEnd() != null && checkOut.isAfter(booking.getTravelEnd())) {
            throw new BadRequestException("Reservation dates must be within the booking dates");
        }
    }

    private void validateRoom(Property property, Room room) {
        if (!room.getProperty().getId().equals(property.getId())) {
            throw new ResourceNotFoundException("Room not found");
        }
        if (!property.isActive() || property.getStatus() != PropertyStatus.ACTIVE) {
            throw new ConflictException("Property is not available for reservations");
        }
        if (!room.isActive() || room.getStatus() != RoomStatus.AVAILABLE) {
            throw new ConflictException("Room is not available for reservations");
        }
    }

    private LocalDate currentTenantDate(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        try {
            return LocalDate.now(ZoneId.of(tenant.getTimezone()));
        } catch (DateTimeException | NullPointerException exception) {
            throw new BadRequestException("Tenant timezone is invalid");
        }
    }

    private Reservation getReservation(UUID id) {
        return reservationRepository.findByIdAndPropertyTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
    }

    private Booking getBooking(UUID id) {
        return bookingRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private Property getProperty(UUID id) {
        return propertyRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
    }

    private Room getRoom(UUID id) {
        return roomRepository.findByIdAndPropertyTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    private RoomAvailabilityResponse toAvailability(Room room, List<Reservation> reservations) {
        return new RoomAvailabilityResponse(
                room.getId(),
                room.getProperty().getId(),
                room.getProperty().getName(),
                room.getName(),
                room.getType(),
                room.getCapacity(),
                room.getStatus(),
                room.getStatus() == RoomStatus.AVAILABLE && reservations.isEmpty(),
                reservations.stream()
                        .map(reservation -> new AvailabilityReservationResponse(
                                reservation.getId(),
                                reservation.getBookingId(),
                                reservation.getCheckIn(),
                                reservation.getCheckOut(),
                                reservation.getStatus(),
                                reservation.getVersion()
                        ))
                        .toList()
        );
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getBookingId(),
                reservation.getProperty().getId(),
                reservation.getProperty().getName(),
                reservation.getRoom().getId(),
                reservation.getRoom().getName(),
                reservation.getCheckIn(),
                reservation.getCheckOut(),
                reservation.getStatus(),
                reservation.isActive(),
                reservation.getVersion(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Reservation was updated by another user");
        }
    }
}
