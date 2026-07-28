package com.rakesh.zentrodesk.inventory.property.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.inventory.property.Property;
import com.rakesh.zentrodesk.inventory.property.PropertyRepository;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
import com.rakesh.zentrodesk.inventory.reservation.ReservationRepository;
import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import com.rakesh.zentrodesk.inventory.room.RoomRepository;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
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
public class PropertyService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "city", "type", "status", "active");

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<PropertyResponse> findAll(int page, int size, String sort, String direction, String search, PropertyType type, PropertyStatus status, Boolean active) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Property> spec = Specification
                .where(SearchSpecificationBuilder.<Property>textContains(search, Set.of("name", "address", "city", "state", "country", "phone", "email", "contactPerson")))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        return PageResponse.from(propertyRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PropertyResponse findById(UUID id) {
        return toResponse(getProperty(id));
    }

    @Transactional
    @AuditMutation(entityType = "PROPERTY", action = "CREATE")
    public PropertyResponse create(PropertyRequest request) {
        Property property = Property.builder()
                .tenantId(currentTenant.id())
                .name(request.name().trim())
                .type(request.type())
                .address(trimToNull(request.address()))
                .city(trimToNull(request.city()))
                .state(trimToNull(request.state()))
                .country(trimToNull(request.country()))
                .phone(trimToNull(request.phone()))
                .email(trimToNull(request.email()))
                .contactPerson(trimToNull(request.contactPerson()))
                .status(request.status() == null ? PropertyStatus.ACTIVE : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(propertyRepository.save(property));
    }

    @Transactional
    @AuditMutation(entityType = "PROPERTY", action = "UPDATE")
    public PropertyResponse update(UUID id, PropertyRequest request) {
        Property property = getProperty(id);
        assertVersion(property.getVersion(), request.version());
        property.setName(request.name().trim());
        property.setType(request.type());
        property.setAddress(trimToNull(request.address()));
        property.setCity(trimToNull(request.city()));
        property.setState(trimToNull(request.state()));
        property.setCountry(trimToNull(request.country()));
        property.setPhone(trimToNull(request.phone()));
        property.setEmail(trimToNull(request.email()));
        property.setContactPerson(trimToNull(request.contactPerson()));
        if (request.status() != null) {
            validateCanDeactivate(property, request.status());
            property.setStatus(request.status());
        }
        if (request.active() != null) {
            if (!request.active()) {
                validateCanDeactivate(property, PropertyStatus.INACTIVE);
            }
            property.setActive(request.active());
        }
        return toResponse(property);
    }

    @Transactional
    @AuditMutation(entityType = "PROPERTY", action = "STATUS_UPDATE")
    public PropertyResponse updateStatus(UUID id, PropertyStatusRequest request) {
        Property property = getProperty(id);
        assertVersion(property.getVersion(), request.version());
        validateCanDeactivate(property, request.status());
        property.setStatus(request.status());
        return toResponse(property);
    }

    @Transactional
    @AuditMutation(entityType = "PROPERTY", action = "DISABLE")
    public void disable(UUID id) {
        Property property = getProperty(id);
        validateCanDeactivate(property, PropertyStatus.INACTIVE);
        property.setStatus(PropertyStatus.INACTIVE);
        property.setActive(false);
    }

    Property getProperty(UUID id) {
        return propertyRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
    }

    private PropertyResponse toResponse(Property property) {
        PropertyInventorySummary inventory = new PropertyInventorySummary(
                roomRepository.countByPropertyIdAndActiveTrue(property.getId()),
                roomRepository.countByPropertyIdAndStatusAndActiveTrue(property.getId(), RoomStatus.AVAILABLE),
                roomRepository.countByPropertyIdAndStatusAndActiveTrue(property.getId(), RoomStatus.UNAVAILABLE),
                roomRepository.countByPropertyIdAndStatusAndActiveTrue(property.getId(), RoomStatus.MAINTENANCE)
        );
        return new PropertyResponse(property.getId(), property.getName(), property.getType(), property.getAddress(), property.getCity(),
                property.getState(), property.getCountry(), property.getPhone(), property.getEmail(), property.getContactPerson(),
                property.getStatus(), property.isActive(), property.getVersion(), inventory,
                property.getCreatedAt(), property.getUpdatedAt());
    }

    private void validateCanDeactivate(Property property, PropertyStatus target) {
        if (target == PropertyStatus.INACTIVE
                && reservationRepository.existsByPropertyIdAndStatusInAndActiveTrue(
                        property.getId(),
                        List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN)
                )) {
            throw new ConflictException("Property has active reservations");
        }
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Property was updated by another user");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
