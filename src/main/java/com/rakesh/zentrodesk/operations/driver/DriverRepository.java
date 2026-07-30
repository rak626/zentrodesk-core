package com.rakesh.zentrodesk.operations.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID>, JpaSpecificationExecutor<Driver> {

    long countByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, DriverStatus status);

    Optional<Driver> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Driver> findByTenantIdAndVehicleIdAndActiveTrue(UUID tenantId, UUID vehicleId);

    boolean existsByTenantIdAndVehicleIdAndActiveTrueAndIdNot(UUID tenantId, UUID vehicleId, UUID id);

    List<Driver> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
}
