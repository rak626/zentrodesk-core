package com.rakesh.zentrodesk.operations.booking.api;

import com.rakesh.zentrodesk.operations.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusRequest(@NotNull BookingStatus status, Long version) {

    public BookingStatusRequest(BookingStatus status) {
        this(status, null);
    }
}
