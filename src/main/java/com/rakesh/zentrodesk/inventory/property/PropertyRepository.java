package com.rakesh.zentrodesk.inventory.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    long countByTenantIdAndActiveTrue(UUID tenantId);

    Optional<Property> findByIdAndTenantId(UUID id, UUID tenantId);
}
