package com.rakesh.zentrodesk.common.observability;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.authentication.AuthenticationMetricsListener;
import com.rakesh.zentrodesk.iam.authentication.AuthenticationSecurityEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

    @Test
    void recordsOnlyBoundedAuthenticationAndApiTags() {
        new AuthenticationMetricsListener(metrics).onAuthenticationSecurityEvent(new AuthenticationSecurityEvent(
                Instant.now(),
                AccountType.TENANT,
                null,
                "LOGIN",
                "REJECTED"
        ));
        metrics.apiRequest("tenant", "GET", 404, Duration.ofMillis(25));

        assertEquals(1.0, registry.get("zentrodesk.authentication.events")
                .tags("account.type", "tenant", "action", "login", "outcome", "rejected")
                .counter()
                .count());
        assertEquals(1L, registry.get("zentrodesk.api.requests")
                .tags("scope", "tenant", "method", "get", "outcome", "4xx")
                .timer()
                .count());
    }

    @Test
    void foldsUnknownLabelsIntoOther() {
        metrics.authentication(AccountType.PLATFORM.name(), "email@example.test", "secret-value");

        assertEquals(1.0, registry.get("zentrodesk.authentication.events")
                .tags("account.type", "platform", "action", "other", "outcome", "other")
                .counter()
                .count());
    }
}
