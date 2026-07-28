package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomResponse(UUID id, UUID propertyId, String propertyName, String name, RoomType type, Integer capacity,
                           BigDecimal basePrice, String amenitiesSummary, RoomStatus status, boolean active, long version,
                           Instant createdAt, Instant updatedAt) {
}
