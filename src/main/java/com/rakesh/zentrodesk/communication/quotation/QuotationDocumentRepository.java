package com.rakesh.zentrodesk.communication.quotation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationDocumentRepository extends JpaRepository<QuotationDocument, UUID> {

    Optional<QuotationDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<QuotationDocument> findFirstByTenantIdAndQuotationIdOrderByDocumentVersionDesc(
            UUID tenantId,
            UUID quotationId
    );

    List<QuotationDocument> findTop20ByGenerationStatusAndNextGenerationAttemptAtLessThanEqualOrderByCreatedAt(
            QuotationDocumentGenerationStatus status,
            Instant nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from QuotationDocument document where document.id = :id")
    Optional<QuotationDocument> findLockedById(@Param("id") UUID id);
}
