package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomRequest(@NotNull UUID propertyId, @NotBlank String name, @NotNull RoomType type,
                          @NotNull Integer capacity, @NotNull BigDecimal basePrice, RoomStatus status, Boolean active) {
}
