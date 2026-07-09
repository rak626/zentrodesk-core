package com.rakesh.zentrodesk.inventory.property.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.inventory.property.Property;
import com.rakesh.zentrodesk.inventory.property.PropertyRepository;
import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;
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
public class PropertyService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "city", "type", "status", "active");

    private final PropertyRepository propertyRepository;
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
    public PropertyResponse update(UUID id, PropertyRequest request) {
        Property property = getProperty(id);
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
            property.setStatus(request.status());
        }
        if (request.active() != null) {
            property.setActive(request.active());
        }
        return toResponse(property);
    }

    @Transactional
    public PropertyResponse updateStatus(UUID id, PropertyStatusRequest request) {
        Property property = getProperty(id);
        property.setStatus(request.status());
        return toResponse(property);
    }

    Property getProperty(UUID id) {
        Property property = propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        currentTenant.require(property.getTenantId());
        return property;
    }

    private PropertyResponse toResponse(Property property) {
        return new PropertyResponse(property.getId(), property.getName(), property.getType(), property.getAddress(), property.getCity(),
                property.getState(), property.getCountry(), property.getPhone(), property.getEmail(), property.getContactPerson(),
                property.getStatus(), property.isActive(), property.getCreatedAt(), property.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
