package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationResponse(UUID id, UUID bookingId, UUID propertyId, String propertyName, UUID roomId, String roomName,
                                  LocalDate checkIn, LocalDate checkOut, ReservationStatus status, boolean active,
                                  long version, Instant createdAt, Instant updatedAt) {
}
