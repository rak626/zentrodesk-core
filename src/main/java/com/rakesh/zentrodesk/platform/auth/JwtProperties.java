package com.rakesh.zentrodesk.platform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        String previousSecret,
        @Positive long accessTokenExpiration,
        @Positive long refreshTokenExpiration
) {

    @AssertTrue(message = "Previous JWT secret must be blank or contain at least 32 characters")
    public boolean isPreviousSecretValid() {
        return previousSecret == null || previousSecret.isBlank() || previousSecret.length() >= 32;
    }
}
