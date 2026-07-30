package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.room.RoomType;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailableRoomResponse(
        UUID roomId,
        UUID propertyId,
        String propertyName,
        String roomName,
        RoomType roomType,
        int capacity,
        BigDecimal basePrice,
        String amenitiesSummary
) {
}
