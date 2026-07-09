package com.rakesh.zentrodesk.operations.driver.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.driver.Driver;
import com.rakesh.zentrodesk.operations.driver.DriverRepository;
import com.rakesh.zentrodesk.operations.driver.DriverStatus;
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
public class DriverService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "status", "active");

    private final DriverRepository driverRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> findAll(int page, int size, String sort, String direction, String search, DriverStatus status, Boolean active) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Driver> spec = Specification
                .where(SearchSpecificationBuilder.<Driver>textContains(search, Set.of("name", "phone", "licenseNumber", "vehicleNumber", "vehicleType")))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        return PageResponse.from(driverRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(UUID id) {
        return toResponse(getDriver(id));
    }

    @Transactional
    public DriverResponse create(DriverRequest request) {
        Driver driver = Driver.builder()
                .tenantId(currentTenant.id())
                .name(request.name().trim())
                .phone(trimToNull(request.phone()))
                .licenseNumber(trimToNull(request.licenseNumber()))
                .vehicleNumber(trimToNull(request.vehicleNumber()))
                .vehicleType(trimToNull(request.vehicleType()))
                .status(request.status() == null ? DriverStatus.AVAILABLE : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse update(UUID id, DriverRequest request) {
        Driver driver = getDriver(id);
        driver.setName(request.name().trim());
        driver.setPhone(trimToNull(request.phone()));
        driver.setLicenseNumber(trimToNull(request.licenseNumber()));
        driver.setVehicleNumber(trimToNull(request.vehicleNumber()));
        driver.setVehicleType(trimToNull(request.vehicleType()));
        if (request.status() != null) {
            driver.setStatus(request.status());
        }
        if (request.active() != null) {
            driver.setActive(request.active());
        }
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse updateStatus(UUID id, DriverStatusRequest request) {
        Driver driver = getDriver(id);
        driver.setStatus(request.status());
        return toResponse(driver);
    }

    @Transactional
    public void delete(UUID id) {
        getDriver(id).setActive(false);
    }

    private Driver getDriver(UUID id) {
        Driver driver = driverRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        currentTenant.require(driver.getTenantId());
        return driver;
    }

    private DriverResponse toResponse(Driver driver) {
        return new DriverResponse(driver.getId(), driver.getName(), driver.getPhone(), driver.getLicenseNumber(), driver.getVehicleNumber(),
                driver.getVehicleType(), driver.getStatus(), driver.isActive(), driver.getCreatedAt(), driver.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
