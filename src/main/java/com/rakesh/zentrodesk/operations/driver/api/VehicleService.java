package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;
import com.rakesh.zentrodesk.operations.assignment.DriverAssignmentRepository;
import com.rakesh.zentrodesk.operations.driver.Vehicle;
import com.rakesh.zentrodesk.operations.driver.VehicleRepository;
import com.rakesh.zentrodesk.operations.driver.VehicleStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "updatedAt", "registrationNumber", "type", "status", "active");
    private static final Set<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
            Set.of(AssignmentStatus.PENDING, AssignmentStatus.CONFIRMED);

    private final VehicleRepository vehicleRepository;
    private final DriverAssignmentRepository assignmentRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            VehicleStatus status,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Vehicle> specification = Specification
                .where(SearchSpecificationBuilder.<Vehicle>textContains(
                        search,
                        Set.of("registrationNumber", "type", "make", "model")
                ))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status));
        return PageResponse.from(vehicleRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(UUID id) {
        return toResponse(getVehicle(id));
    }

    @Transactional
    @AuditMutation(entityType = "VEHICLE", action = "CREATE")
    public VehicleResponse create(VehicleRequest request) {
        Vehicle vehicle = Vehicle.builder()
                .tenantId(currentTenant.id())
                .registrationNumber(normalizeRegistration(request.registrationNumber()))
                .type(request.type().trim())
                .make(trimToNull(request.make()))
                .model(trimToNull(request.model()))
                .capacity(request.capacity())
                .status(request.status() == null ? VehicleStatus.AVAILABLE : request.status())
                .active(request.active() == null || request.active())
                .build();
        return save(vehicle);
    }

    @Transactional
    @AuditMutation(entityType = "VEHICLE", action = "UPDATE")
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = getVehicle(id);
        assertVersion(vehicle.getVersion(), request.version());
        if (Boolean.FALSE.equals(request.active())
                || request.status() != null && request.status() != VehicleStatus.AVAILABLE) {
            ensureNoActiveAssignments(vehicle);
        }
        vehicle.setRegistrationNumber(normalizeRegistration(request.registrationNumber()));
        vehicle.setType(request.type().trim());
        vehicle.setMake(trimToNull(request.make()));
        vehicle.setModel(trimToNull(request.model()));
        vehicle.setCapacity(request.capacity());
        if (request.status() != null) {
            vehicle.setStatus(request.status());
        }
        if (request.active() != null) {
            vehicle.setActive(request.active());
        }
        return save(vehicle);
    }

    @Transactional
    @AuditMutation(entityType = "VEHICLE", action = "STATUS_UPDATE")
    public VehicleResponse updateStatus(UUID id, VehicleStatusRequest request) {
        Vehicle vehicle = getVehicle(id);
        assertVersion(vehicle.getVersion(), request.version());
        if (request.status() != VehicleStatus.AVAILABLE) {
            ensureNoActiveAssignments(vehicle);
        }
        vehicle.setStatus(request.status());
        return toResponse(vehicle);
    }

    @Transactional
    @AuditMutation(entityType = "VEHICLE", action = "DISABLE")
    public void delete(UUID id) {
        Vehicle vehicle = getVehicle(id);
        if (!vehicle.isActive()) {
            return;
        }
        ensureNoActiveAssignments(vehicle);
        vehicle.setActive(false);
    }

    private VehicleResponse save(Vehicle vehicle) {
        try {
            return toResponse(vehicleRepository.saveAndFlush(vehicle));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Vehicle registration already exists");
        }
    }

    private Vehicle getVehicle(UUID id) {
        return vehicleRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
    }

    private void ensureNoActiveAssignments(Vehicle vehicle) {
        if (assignmentRepository.existsByTenantIdAndVehicleIdAndActiveTrueAndStatusIn(
                currentTenant.id(),
                vehicle.getId(),
                ACTIVE_ASSIGNMENT_STATUSES
        )) {
            throw new ConflictException("Vehicle has an active assignment");
        }
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getType(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getCapacity(),
                vehicle.getStatus(),
                vehicle.isActive(),
                vehicle.getVersion(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }

    private void assertVersion(long currentVersion, Long requestedVersion) {
        if (requestedVersion != null && currentVersion != requestedVersion) {
            throw new ConflictException("Vehicle was updated by another request");
        }
    }

    private String normalizeRegistration(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
