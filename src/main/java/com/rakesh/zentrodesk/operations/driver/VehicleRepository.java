package com.rakesh.zentrodesk.operations.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Vehicle> findByTenantIdAndRegistrationNumberIgnoreCase(UUID tenantId, String registrationNumber);

    List<Vehicle> findByTenantIdAndActiveTrueOrderByRegistrationNumberAsc(UUID tenantId);
}
