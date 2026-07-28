package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.exception.AuthenticationRateLimitException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthenticationThrottle {

    private static final int MAX_FAILURES = 5;
    private static final int MAX_ENTRIES = 10_000;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthenticationThrottle() {
        this(Clock.systemUTC());
    }

    AuthenticationThrottle(Clock clock) {
        this.clock = clock;
    }

    public void requireAllowed(AccountType accountType, String email) {
        String key = key(accountType, email);
        Instant now = clock.instant();
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        if (attempt.blockedUntil() != null && attempt.blockedUntil().isAfter(now)) {
            throw new AuthenticationRateLimitException();
        }
        if (attempt.windowStartedAt().plus(ATTEMPT_WINDOW).isBefore(now)) {
            attempts.remove(key, attempt);
        }
    }

    public void recordFailure(AccountType accountType, String email) {
        String key = key(accountType, email);
        Instant now = clock.instant();
        attempts.compute(key, (unused, previous) -> {
            int failures = previous == null || previous.windowStartedAt().plus(ATTEMPT_WINDOW).isBefore(now)
                    ? 1
                    : previous.failures() + 1;
            Instant windowStartedAt = failures == 1 ? now : previous.windowStartedAt();
            Instant blockedUntil = failures >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : null;
            return new Attempt(failures, windowStartedAt, blockedUntil);
        });
        evictExpired(now);
    }

    public void recordSuccess(AccountType accountType, String email) {
        attempts.remove(key(accountType, email));
    }

    private void evictExpired(Instant now) {
        if (attempts.size() <= MAX_ENTRIES) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().windowStartedAt().plus(ATTEMPT_WINDOW).isBefore(now));
        if (attempts.size() > MAX_ENTRIES) {
            attempts.keySet().stream().findFirst().ifPresent(attempts::remove);
        }
    }

    private String key(AccountType accountType, String email) {
        return accountType.name() + ":" + email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(int failures, Instant windowStartedAt, Instant blockedUntil) {
    }
}
