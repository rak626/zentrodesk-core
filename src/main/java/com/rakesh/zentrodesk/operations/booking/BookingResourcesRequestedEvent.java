package com.rakesh.zentrodesk.operations.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BookingResourcesRequestedEvent(
        UUID tenantId,
        UUID bookingId,
        UUID quotationId,
        int adults,
        List<RoomBlock> rooms,
        List<TransportBlock> transports,
        List<GuideBlock> guides
) {

    public record RoomBlock(
            UUID quotationItemId,
            UUID roomId,
            LocalDate checkIn,
            LocalDate checkOut,
            boolean capacityOverridden
    ) {
    }

    public record TransportBlock(
            UUID quotationItemId,
            UUID vehicleId,
            Instant pickupAt,
            Instant dropoffAt,
            String pickupLocation,
            String dropoffLocation,
            boolean capacityOverridden
    ) {
    }

    public record GuideBlock(UUID guideId, Instant startsAt, Instant endsAt) {
    }
}
