package com.rakesh.zentrodesk.platform.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TenantEntitlementRepository extends JpaRepository<TenantEntitlement, UUID> {

    List<TenantEntitlement> findAllByTenantIdOrderByFeatureKey(UUID tenantId);

    Optional<TenantEntitlement> findByTenantIdAndFeatureKey(UUID tenantId, String featureKey);
}
