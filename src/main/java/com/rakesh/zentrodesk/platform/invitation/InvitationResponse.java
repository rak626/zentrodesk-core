package com.rakesh.zentrodesk.platform.invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID userId,
        UUID roleId,
        String roleName,
        String firstName,
        String lastName,
        String email,
        InvitationStatus status,
        Instant expiresAt,
        Instant createdAt
) {
}
