package com.rakesh.zentrodesk.inventory.reservation;

import java.util.UUID;

public record ReservationChangedEvent(
        UUID tenantId,
        UUID reservationId,
        UUID bookingId,
        ReservationStatus status
) {
}
