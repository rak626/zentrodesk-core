package com.rakesh.zentrodesk.sales.lead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface LeadFollowUpRepository
        extends JpaRepository<LeadFollowUp, UUID>, JpaSpecificationExecutor<LeadFollowUp> {

    Optional<LeadFollowUp> findByIdAndTenantId(UUID id, UUID tenantId);
}
