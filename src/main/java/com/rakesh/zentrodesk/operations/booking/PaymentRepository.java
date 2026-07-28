package com.rakesh.zentrodesk.operations.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByBookingIdAndActiveTrueOrderByCreatedAtDesc(UUID bookingId);

    Optional<Payment> findByIdAndBookingTenantId(UUID id, UUID tenantId);

    Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.booking.id = :bookingId
              and p.active = true
              and p.status in :statuses
            """)
    BigDecimal sumActiveByBookingAndStatuses(@Param("bookingId") UUID bookingId, @Param("statuses") List<PaymentStatus> statuses);
}
