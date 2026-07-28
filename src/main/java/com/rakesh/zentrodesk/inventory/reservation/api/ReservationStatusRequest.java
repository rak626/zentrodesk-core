package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public record ReservationStatusRequest(@NotNull ReservationStatus status, Long version) {

    public ReservationStatusRequest(ReservationStatus status) {
        this(status, null);
    }
}
