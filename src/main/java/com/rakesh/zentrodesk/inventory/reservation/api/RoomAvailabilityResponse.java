package com.rakesh.zentrodesk.inventory.reservation.api;

import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;

import java.util.List;
import java.util.UUID;

public record RoomAvailabilityResponse(
        UUID roomId,
        UUID propertyId,
        String propertyName,
        String roomName,
        RoomType roomType,
        int capacity,
        RoomStatus roomStatus,
        boolean availableForEntireRange,
        List<AvailabilityReservationResponse> reservations
) {
}
