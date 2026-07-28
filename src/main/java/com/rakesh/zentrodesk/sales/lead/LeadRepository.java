package com.rakesh.zentrodesk.sales.lead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, LeadStatus status);

    boolean existsByTenantIdAndNormalizedPhoneAndActiveTrueAndIdNot(
            UUID tenantId,
            String normalizedPhone,
            UUID id
    );

    boolean existsByTenantIdAndNormalizedPhoneAndActiveTrue(UUID tenantId, String normalizedPhone);

    @Query("""
            select lead.status, count(lead)
            from Lead lead
            where lead.tenantId = :tenantId and lead.active = true
            group by lead.status
            """)
    List<Object[]> countPipeline(@Param("tenantId") UUID tenantId);
}
