package com.rakesh.zentrodesk.operations.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BookingGuestRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 10) String gender,
        LocalDate dateOfBirth,
        @Size(max = 20) String phone,
        @Size(max = 50) String identityType,
        @Size(max = 100) String identityNumber,
        Boolean primaryGuest
) {
}
