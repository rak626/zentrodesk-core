package com.rakesh.zentrodesk.sales.quotation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID>, JpaSpecificationExecutor<Quotation> {

    Optional<Quotation> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quotation from Quotation quotation where quotation.id = :id and quotation.tenantId = :tenantId")
    Optional<Quotation> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<Quotation> findByTenantIdAndSeriesId(UUID tenantId, UUID seriesId);

    List<Quotation> findAllByTenantIdAndLeadId(UUID tenantId, UUID leadId);

    boolean existsByIdAndTenantIdAndStatus(UUID id, UUID tenantId, QuotationStatus status);
}
