package com.rakesh.zentrodesk.communication.notification.api;

import com.rakesh.zentrodesk.communication.notification.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String category,
        String deepLink,
        String sourceType,
        UUID sourceId,
        NotificationStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
