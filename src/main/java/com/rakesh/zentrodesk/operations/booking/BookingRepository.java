package com.rakesh.zentrodesk.operations.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    long countByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, BookingStatus status);

    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Booking> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<Booking> findByTenantIdAndQuotationId(UUID tenantId, UUID quotationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id and booking.tenantId = :tenantId")
    Optional<Booking> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
