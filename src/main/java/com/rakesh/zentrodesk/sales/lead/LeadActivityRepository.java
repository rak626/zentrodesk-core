package com.rakesh.zentrodesk.sales.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    Page<LeadActivity> findByTenantIdAndLeadId(UUID tenantId, UUID leadId, Pageable pageable);
}
