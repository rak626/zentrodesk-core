package com.rakesh.zentrodesk.operations.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingGuestRepository extends JpaRepository<BookingGuest, UUID> {

    List<BookingGuest> findByBookingIdAndActiveTrueOrderByPrimaryGuestDescCreatedAtAsc(UUID bookingId);

    Optional<BookingGuest> findByIdAndBookingTenantId(UUID id, UUID tenantId);
}
