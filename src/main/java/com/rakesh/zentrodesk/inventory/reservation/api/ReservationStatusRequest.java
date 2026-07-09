package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public record ReservationStatusRequest(@NotNull ReservationStatus status) {
}
