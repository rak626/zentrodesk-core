package com.rakesh.zentrodesk.inventory.reservation.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationTransferRequest(@NotNull UUID roomId, Long version) {
}
