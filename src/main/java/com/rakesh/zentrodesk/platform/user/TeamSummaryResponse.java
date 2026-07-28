package com.rakesh.zentrodesk.platform.user;

public record TeamSummaryResponse(
        int seatLimit,
        long occupiedSeats,
        long activeUsers,
        long availableSeats
) {
}
