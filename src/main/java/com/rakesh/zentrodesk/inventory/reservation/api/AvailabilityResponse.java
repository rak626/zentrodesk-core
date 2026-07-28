package com.rakesh.zentrodesk.inventory.reservation.api;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        AvailabilityView view,
        LocalDate from,
        LocalDate to,
        List<LocalDate> dates,
        List<RoomAvailabilityResponse> rooms
) {
}
