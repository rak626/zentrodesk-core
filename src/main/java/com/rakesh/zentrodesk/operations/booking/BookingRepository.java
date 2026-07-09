package com.rakesh.zentrodesk.operations.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    long countByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, BookingStatus status);
}
