package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationRequest(@NotNull UUID bookingId, @NotNull UUID propertyId, @NotNull UUID roomId,
                                 @NotNull LocalDate checkIn, @NotNull LocalDate checkOut, ReservationStatus status, Boolean active) {
}
