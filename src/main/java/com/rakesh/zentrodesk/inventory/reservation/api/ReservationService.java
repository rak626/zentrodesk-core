package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.inventory.property.Property;
import com.rakesh.zentrodesk.inventory.property.PropertyRepository;
import com.rakesh.zentrodesk.inventory.reservation.Reservation;
import com.rakesh.zentrodesk.inventory.reservation.ReservationRepository;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.inventory.room.Room;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "checkIn", "checkOut", "status", "active");

    private final ReservationRepository reservationRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findAll(int page, int size, String sort, String direction, UUID bookingId,
                                                     UUID propertyId, UUID roomId, ReservationStatus status, LocalDate from, LocalDate to) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Reservation> spec = Specification
                .where(SearchSpecificationBuilder.<Reservation>uuidEquals("property.tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("bookingId", bookingId))
                .and(SearchSpecificationBuilder.uuidEquals("property.id", propertyId))
                .and(SearchSpecificationBuilder.uuidEquals("room.id", roomId))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("checkIn"), from))
                .and((root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("checkOut"), to));
        return PageResponse.from(reservationRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional
    public ReservationResponse create(ReservationRequest request) {
        Property property = getProperty(request.propertyId());
        Room room = getRoom(request.roomId());
        currentTenant.require(room.getProperty().getTenantId());
        Reservation reservation = Reservation.builder()
                .bookingId(request.bookingId())
                .property(property)
                .room(room)
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .status(request.status() == null ? ReservationStatus.RESERVED : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse updateStatus(UUID id, ReservationStatusRequest request) {
        Reservation reservation = getReservation(id);
        reservation.setStatus(request.status());
        return toResponse(reservation);
    }

    private Reservation getReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        currentTenant.require(reservation.getProperty().getTenantId());
        return reservation;
    }

    private Property getProperty(UUID id) {
        Property property = propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        currentTenant.require(property.getTenantId());
        return property;
    }

    private Room getRoom(UUID id) {
        Room room = roomRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        currentTenant.require(room.getProperty().getTenantId());
        return room;
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getBookingId(), reservation.getProperty().getId(),
                reservation.getProperty().getName(), reservation.getRoom().getId(), reservation.getRoom().getName(),
                reservation.getCheckIn(), reservation.getCheckOut(), reservation.getStatus(), reservation.isActive(),
                reservation.getCreatedAt(), reservation.getUpdatedAt());
    }
}
