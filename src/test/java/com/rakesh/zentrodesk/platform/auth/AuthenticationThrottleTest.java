package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.exception.AuthenticationRateLimitException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationThrottleTest {

    private final AuthenticationThrottle throttle = new AuthenticationThrottle();

    @Test
    void blocksAfterFiveFailuresAndClearsOnSuccess() {
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure(AccountType.TENANT, "user@example.com");
        }

        assertThrows(
                AuthenticationRateLimitException.class,
                () -> throttle.requireAllowed(AccountType.TENANT, "USER@example.com")
        );

        throttle.recordSuccess(AccountType.TENANT, "user@example.com");
        assertDoesNotThrow(() -> throttle.requireAllowed(AccountType.TENANT, "user@example.com"));
    }

    @Test
    void keepsTenantAndPlatformThrottleKeysSeparate() {
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure(AccountType.TENANT, "user@example.com");
        }

        assertDoesNotThrow(() -> throttle.requireAllowed(AccountType.PLATFORM, "user@example.com"));
    }
}
