package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomRequest(@NotNull UUID propertyId, @NotBlank String name, @NotNull RoomType type,
                          @NotNull @Positive Integer capacity,
                          @NotNull @DecimalMin("0.00") BigDecimal basePrice,
                          @Size(max = 500) String amenitiesSummary,
                          RoomStatus status, Boolean active, Long version) {
}
