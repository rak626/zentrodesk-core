package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.inventory.property.Property;
import com.rakesh.zentrodesk.inventory.property.PropertyRepository;
import com.rakesh.zentrodesk.inventory.room.Room;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.reservation.ReservationRepository;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "type", "status", "active", "basePrice");

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            UUID propertyId,
            RoomType type,
            RoomStatus status,
            Integer minCapacity,
            Integer maxCapacity,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Room> spec = Specification
                .where(SearchSpecificationBuilder.<Room>textContains(search, Set.of("name", "amenitiesSummary")))
                .and(SearchSpecificationBuilder.uuidEquals("property.tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("property.id", propertyId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> minCapacity == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity))
                .and((root, query, cb) -> maxCapacity == null
                        ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.get("capacity"), maxCapacity));
        return PageResponse.from(roomRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(UUID id) {
        return toResponse(getRoom(id));
    }

    @Transactional
    @AuditMutation(entityType = "ROOM", action = "CREATE")
    public RoomResponse create(RoomRequest request) {
        Property property = getProperty(request.propertyId());
        if (!property.isActive()) {
            throw new ConflictException("Rooms cannot be added to a disabled property");
        }
        Room room = Room.builder()
                .property(property)
                .name(request.name().trim())
                .type(request.type())
                .capacity(request.capacity())
                .basePrice(request.basePrice())
                .amenitiesSummary(trimToNull(request.amenitiesSummary()))
                .status(request.status() == null ? RoomStatus.AVAILABLE : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(roomRepository.save(room));
    }

    @Transactional
    @AuditMutation(entityType = "ROOM", action = "UPDATE")
    public RoomResponse update(UUID id, RoomRequest request) {
        Room room = getRoom(id);
        assertVersion(room.getVersion(), request.version());
        Property property = getProperty(request.propertyId());
        if (!property.isActive()) {
            throw new ConflictException("Rooms cannot be moved to a disabled property");
        }
        room.setProperty(property);
        room.setName(request.name().trim());
        room.setType(request.type());
        room.setCapacity(request.capacity());
        room.setBasePrice(request.basePrice());
        room.setAmenitiesSummary(trimToNull(request.amenitiesSummary()));
        if (request.status() != null) {
            validateNoActiveReservations(room, request.status());
            room.setStatus(request.status());
        }
        if (request.active() != null) {
            if (!request.active()) {
                validateNoActiveReservations(room, RoomStatus.UNAVAILABLE);
            }
            room.setActive(request.active());
        }
        return toResponse(room);
    }

    @Transactional
    @AuditMutation(entityType = "ROOM", action = "STATUS_UPDATE")
    public RoomResponse updateStatus(UUID id, RoomStatusRequest request) {
        Room room = getRoom(id);
        assertVersion(room.getVersion(), request.version());
        validateNoActiveReservations(room, request.status());
        room.setStatus(request.status());
        return toResponse(room);
    }

    @Transactional
    @AuditMutation(entityType = "ROOM", action = "DISABLE")
    public void disable(UUID id) {
        Room room = getRoom(id);
        validateNoActiveReservations(room, RoomStatus.UNAVAILABLE);
        room.setStatus(RoomStatus.UNAVAILABLE);
        room.setActive(false);
    }

    Room getRoom(UUID id) {
        return roomRepository.findByIdAndPropertyTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    private Property getProperty(UUID id) {
        return propertyRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(room.getId(), room.getProperty().getId(), room.getProperty().getName(), room.getName(), room.getType(),
                room.getCapacity(), room.getBasePrice(), room.getAmenitiesSummary(), room.getStatus(), room.isActive(),
                room.getVersion(), room.getCreatedAt(), room.getUpdatedAt());
    }

    private void validateNoActiveReservations(Room room, RoomStatus target) {
        if (target != RoomStatus.AVAILABLE
                && reservationRepository.existsByRoomIdAndStatusInAndActiveTrue(
                        room.getId(),
                        List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN)
                )) {
            throw new ConflictException("Room has active reservations");
        }
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Room was updated by another user");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
