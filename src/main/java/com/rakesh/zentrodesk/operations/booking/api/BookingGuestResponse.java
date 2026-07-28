package com.rakesh.zentrodesk.operations.booking.api;

import java.time.LocalDate;
import java.util.UUID;

public record BookingGuestResponse(
        UUID id,
        String name,
        String gender,
        LocalDate dateOfBirth,
        String phone,
        String identityType,
        String identityNumber,
        boolean primaryGuest
) {
}
