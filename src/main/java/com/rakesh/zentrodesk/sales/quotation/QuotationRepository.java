package com.rakesh.zentrodesk.sales.quotation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID>, JpaSpecificationExecutor<Quotation> {

    Optional<Quotation> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantIdAndStatus(UUID id, UUID tenantId, QuotationStatus status);
}
