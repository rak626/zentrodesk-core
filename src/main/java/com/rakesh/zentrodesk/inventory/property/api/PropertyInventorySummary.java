package com.rakesh.zentrodesk.inventory.property.api;

public record PropertyInventorySummary(long totalRooms, long availableRooms, long unavailableRooms, long maintenanceRooms) {
}
