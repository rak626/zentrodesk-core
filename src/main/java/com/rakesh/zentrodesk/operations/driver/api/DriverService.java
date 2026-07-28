package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.assignment.AssignmentStatus;
import com.rakesh.zentrodesk.operations.assignment.DriverAssignmentRepository;
import com.rakesh.zentrodesk.operations.driver.Driver;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
import com.rakesh.zentrodesk.operations.driver.DriverStatusHistory;
import com.rakesh.zentrodesk.operations.driver.DriverStatusHistoryRepository;
import com.rakesh.zentrodesk.operations.driver.Vehicle;
import com.rakesh.zentrodesk.operations.driver.VehicleRepository;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "updatedAt", "name", "status", "active");
    private static final Set<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
            Set.of(AssignmentStatus.PENDING, AssignmentStatus.CONFIRMED);

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverStatusHistoryRepository historyRepository;
    private final DriverAssignmentRepository assignmentRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            DriverStatus status,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        boolean includeSensitive = canViewSensitiveFields();
        Set<String> searchFields = includeSensitive
                ? Set.of("name", "phone", "licenseNumber", "vehicleNumber", "vehicleType")
                : Set.of("name", "vehicleNumber", "vehicleType");
        Specification<Driver> spec = Specification
                .where(SearchSpecificationBuilder.<Driver>textContains(
                        search,
                        searchFields
                ))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status));
        return PageResponse.from(driverRepository.findAll(spec, pageable)
                .map(driver -> toResponse(driver, includeSensitive)));
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(UUID id) {
        return toResponse(getDriver(id), canViewSensitiveFields());
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverStatusHistoryResponse> history(UUID id, int page, int size) {
        Driver driver = getDriver(id);
        Pageable pageable = PaginationUtils.pageable(
                page,
                size,
                "createdAt",
                "DESC",
                Set.of("createdAt")
        );
        return PageResponse.from(historyRepository
                .findByTenantIdAndDriverId(currentTenant.id(), driver.getId(), pageable)
                .map(entry -> new DriverStatusHistoryResponse(
                        entry.getId(),
                        entry.getPreviousStatus(),
                        entry.getStatus(),
                        entry.getReason(),
                        entry.getChangedBy(),
                        entry.getCreatedAt()
                )));
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER", action = "CREATE")
    public DriverResponse create(DriverRequest request) {
        Vehicle vehicle = getVehicle(request.vehicleId());
        DriverStatus status = request.status() == null ? DriverStatus.AVAILABLE : request.status();
        if (status == DriverStatus.ON_TRIP) {
            throw new BadRequestException("ON_TRIP is controlled by confirmed assignments");
        }
        Driver driver = Driver.builder()
                .tenantId(currentTenant.id())
                .name(request.name().trim())
                .phone(trimToNull(request.phone()))
                .licenseNumber(trimToNull(request.licenseNumber()))
                .vehicle(vehicle)
                .vehicleNumber(vehicle == null
                        ? trimToNull(request.vehicleNumber())
                        : vehicle.getRegistrationNumber())
                .vehicleType(vehicle == null ? trimToNull(request.vehicleType()) : vehicle.getType())
                .status(status)
                .active(request.active() == null || request.active())
                .build();
        driver = driverRepository.save(driver);
        recordStatus(driver, null, status, "Driver created");
        return toResponse(driver, canViewSensitiveFields());
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER", action = "UPDATE")
    public DriverResponse update(UUID id, DriverRequest request) {
        Driver driver = getDriver(id);
        assertVersion(driver.getVersion(), request.version());
        Vehicle vehicle = getVehicle(request.vehicleId());
        if (Boolean.FALSE.equals(request.active())) {
            ensureNoActiveAssignments(driver);
        }
        driver.setName(request.name().trim());
        driver.setPhone(trimToNull(request.phone()));
        driver.setLicenseNumber(trimToNull(request.licenseNumber()));
        driver.setVehicle(vehicle);
        driver.setVehicleNumber(vehicle == null
                ? trimToNull(request.vehicleNumber())
                : vehicle.getRegistrationNumber());
        driver.setVehicleType(vehicle == null
                ? trimToNull(request.vehicleType())
                : vehicle.getType());
        if (request.status() != null && request.status() != driver.getStatus()) {
            changeStatus(driver, request.status(), "Driver details updated");
        }
        if (request.active() != null) {
            driver.setActive(request.active());
        }
        return toResponse(driver, canViewSensitiveFields());
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER", action = "STATUS_UPDATE")
    public DriverResponse updateStatus(UUID id, DriverStatusRequest request) {
        Driver driver = getDriver(id);
        assertVersion(driver.getVersion(), request.version());
        if (driver.getStatus() != request.status()) {
            changeStatus(driver, request.status(), trimToNull(request.reason()));
        }
        return toResponse(driver, canViewSensitiveFields());
    }

    @Transactional
    @AuditMutation(entityType = "DRIVER", action = "DISABLE")
    public void delete(UUID id) {
        Driver driver = getDriver(id);
        if (!driver.isActive()) {
            return;
        }
        ensureNoActiveAssignments(driver);
        driver.setActive(false);
    }

    private void changeStatus(Driver driver, DriverStatus status, String reason) {
        if (driver.getStatus() == DriverStatus.ON_TRIP || status == DriverStatus.ON_TRIP) {
            throw new BadRequestException("ON_TRIP is controlled by confirmed assignments");
        }
        if (status == DriverStatus.UNAVAILABLE) {
            ensureNoActiveAssignments(driver);
        }
        DriverStatus previous = driver.getStatus();
        driver.setStatus(status);
        recordStatus(driver, previous, status, reason);
    }

    private void ensureNoActiveAssignments(Driver driver) {
        if (assignmentRepository.existsByTenantIdAndDriverIdAndActiveTrueAndStatusIn(
                currentTenant.id(),
                driver.getId(),
                ACTIVE_ASSIGNMENT_STATUSES
        )) {
            throw new ConflictException("Driver has an active assignment");
        }
    }

    private Driver getDriver(UUID id) {
        return driverRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
    }

    private Vehicle getVehicle(UUID id) {
        if (id == null) {
            return null;
        }
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        if (!vehicle.isActive()) {
            throw new ConflictException("Vehicle is disabled");
        }
        return vehicle;
    }

    private void recordStatus(
            Driver driver,
            DriverStatus previousStatus,
            DriverStatus status,
            String reason
    ) {
        historyRepository.save(DriverStatusHistory.builder()
                .tenantId(driver.getTenantId())
                .driver(driver)
                .previousStatus(previousStatus)
                .status(status)
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

    private boolean canViewSensitiveFields() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> TenantPermissions.DRIVER_UPDATE.equals(authority.getAuthority()));
    }

    DriverResponse toResponse(Driver driver, boolean includeSensitive) {
        Vehicle vehicle = driver.getVehicle();
        return new DriverResponse(
                driver.getId(),
                driver.getName(),
                includeSensitive ? driver.getPhone() : null,
                includeSensitive ? driver.getLicenseNumber() : null,
                vehicle == null ? null : vehicle.getId(),
                vehicle == null ? driver.getVehicleNumber() : vehicle.getRegistrationNumber(),
                vehicle == null ? driver.getVehicleType() : vehicle.getType(),
                driver.getStatus(),
                driver.isActive(),
                includeSensitive,
                driver.getVersion(),
                driver.getCreatedAt(),
                driver.getUpdatedAt()
        );
    }

    private void assertVersion(long currentVersion, Long requestedVersion) {
        if (requestedVersion != null && currentVersion != requestedVersion) {
            throw new ConflictException("Driver was updated by another request");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
