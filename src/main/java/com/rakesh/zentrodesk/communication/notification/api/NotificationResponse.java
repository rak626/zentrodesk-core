package com.rakesh.zentrodesk.communication.notification.api;

import com.rakesh.zentrodesk.communication.notification.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID userId, String title, String message, NotificationStatus status,
                                   boolean active, Instant createdAt, Instant updatedAt) {
}
