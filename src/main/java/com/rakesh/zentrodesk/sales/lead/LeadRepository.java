package com.rakesh.zentrodesk.sales.lead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    long countByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, LeadStatus status);
}
