package com.rakesh.zentrodesk.platform.invitation;

import java.time.Instant;

public record InvitationDetailsResponse(
        String email,
        String firstName,
        String tenantName,
        String roleName,
        Instant expiresAt
) {
}
