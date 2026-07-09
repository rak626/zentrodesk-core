package com.rakesh.zentrodesk.platform.auth;

import java.time.Instant;

public record RefreshTokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
