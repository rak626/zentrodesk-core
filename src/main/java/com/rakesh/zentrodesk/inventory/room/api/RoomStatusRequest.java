package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import jakarta.validation.constraints.NotNull;

public record RoomStatusRequest(@NotNull RoomStatus status) {
}
