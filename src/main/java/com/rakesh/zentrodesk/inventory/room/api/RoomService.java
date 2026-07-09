package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.inventory.property.Property;
import com.rakesh.zentrodesk.inventory.property.PropertyRepository;
import com.rakesh.zentrodesk.inventory.room.Room;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "type", "status", "active", "basePrice");

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> findAll(int page, int size, String sort, String direction, UUID propertyId, RoomType type, RoomStatus status, Boolean active) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Room> spec = Specification
                .where(SearchSpecificationBuilder.<Room>uuidEquals("property.tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("property.id", propertyId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        return PageResponse.from(roomRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(UUID id) {
        return toResponse(getRoom(id));
    }

    @Transactional
    public RoomResponse create(RoomRequest request) {
        Property property = getProperty(request.propertyId());
        Room room = Room.builder()
                .property(property)
                .name(request.name().trim())
                .type(request.type())
                .capacity(request.capacity())
                .basePrice(request.basePrice())
                .status(request.status() == null ? RoomStatus.AVAILABLE : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse update(UUID id, RoomRequest request) {
        Room room = getRoom(id);
        room.setProperty(getProperty(request.propertyId()));
        room.setName(request.name().trim());
        room.setType(request.type());
        room.setCapacity(request.capacity());
        room.setBasePrice(request.basePrice());
        if (request.status() != null) {
            room.setStatus(request.status());
        }
        if (request.active() != null) {
            room.setActive(request.active());
        }
        return toResponse(room);
    }

    @Transactional
    public RoomResponse updateStatus(UUID id, RoomStatusRequest request) {
        Room room = getRoom(id);
        room.setStatus(request.status());
        return toResponse(room);
    }

    Room getRoom(UUID id) {
        Room room = roomRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        currentTenant.require(room.getProperty().getTenantId());
        return room;
    }

    private Property getProperty(UUID id) {
        Property property = propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        currentTenant.require(property.getTenantId());
        return property;
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(room.getId(), room.getProperty().getId(), room.getProperty().getName(), room.getName(), room.getType(),
                room.getCapacity(), room.getBasePrice(), room.getStatus(), room.isActive(), room.getCreatedAt(), room.getUpdatedAt());
    }
}
