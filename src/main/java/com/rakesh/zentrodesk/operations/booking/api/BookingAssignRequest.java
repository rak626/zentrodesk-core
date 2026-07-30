package com.rakesh.zentrodesk.operations.booking.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookingAssignRequest(@NotNull UUID assignedUserId, Long version) {
}
