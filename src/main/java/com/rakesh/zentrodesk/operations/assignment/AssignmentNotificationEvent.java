package com.rakesh.zentrodesk.operations.assignment;

import java.util.UUID;

public record AssignmentNotificationEvent(
        UUID tenantId,
        UUID assignmentId,
        UUID bookingId,
        String audience,
        AssignmentAction eventType
) {
}
