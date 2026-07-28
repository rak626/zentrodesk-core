package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityReservationResponse(
        UUID id,
        UUID bookingId,
        LocalDate checkIn,
        LocalDate checkOut,
        ReservationStatus status,
        long version
) {
}
