package com.rakesh.zentrodesk.analytics.search;

import com.rakesh.zentrodesk.common.exception.SearchRateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchRateLimiterTest {

    @Test
    void limitsEachUserWithoutSharingTheWindowAcrossUsers() {
        SearchRateLimiter limiter = new SearchRateLimiter(
                Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC)
        );
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();

        for (int request = 0; request < 30; request++) {
            assertDoesNotThrow(() -> limiter.requireAllowed(firstUser));
        }

        assertThrows(SearchRateLimitException.class, () -> limiter.requireAllowed(firstUser));
        assertDoesNotThrow(() -> limiter.requireAllowed(secondUser));
    }
}
