package com.rakesh.zentrodesk.analytics.search;

import com.rakesh.zentrodesk.common.exception.SearchRateLimitException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SearchRateLimiter {

    private static final int MAX_REQUESTS = 30;
    private static final int MAX_ENTRIES = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public SearchRateLimiter() {
        this(Clock.systemUTC());
    }

    SearchRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void requireAllowed(UUID userId) {
        Instant now = clock.instant();
        Window window = windows.compute(userId, (unused, previous) -> {
            if (previous == null || previous.startedAt().plus(WINDOW).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(previous.startedAt(), previous.requests() + 1);
        });
        if (windows.size() > MAX_ENTRIES) {
            windows.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(WINDOW).isBefore(now));
        }
        if (window.requests() > MAX_REQUESTS) {
            throw new SearchRateLimitException();
        }
    }

    private record Window(Instant startedAt, int requests) {
    }
}
