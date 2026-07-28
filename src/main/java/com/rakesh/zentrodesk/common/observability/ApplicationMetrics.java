package com.rakesh.zentrodesk.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ApplicationMetrics {

    private final MeterRegistry meterRegistry;

    public void authentication(String accountType, String action, String outcome) {
        meterRegistry.counter(
                "zentrodesk.authentication.events",
                "account.type", bounded(accountType),
                "action", bounded(action),
                "outcome", bounded(outcome)
        ).increment();
    }

    public void authorizationDenied() {
        meterRegistry.counter("zentrodesk.authorization.denied").increment();
    }

    public void tenantScopeMiss() {
        meterRegistry.counter("zentrodesk.tenant.scope.misses").increment();
    }

    public void emailDelivery(String outcome) {
        meterRegistry.counter(
                "zentrodesk.email.delivery",
                "outcome", bounded(outcome)
        ).increment();
    }

    public void apiRequest(String scope, String method, int status, Duration duration) {
        meterRegistry.timer(
                "zentrodesk.api.requests",
                "scope", bounded(scope),
                "method", bounded(method),
                "outcome", statusOutcome(status)
        ).record(duration);
    }

    private String statusOutcome(int status) {
        if (status < 100 || status > 599) {
            return "unknown";
        }
        return status / 100 + "xx";
    }

    private String bounded(String value) {
        return switch (value == null ? "" : value.toUpperCase()) {
            case "TENANT", "PLATFORM", "ACTUATOR", "OTHER",
                    "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD",
                    "LOGIN", "REFRESH", "LOGOUT",
                    "SUCCESS", "REJECTED", "SENT", "RETRY", "FAILED" -> value.toLowerCase();
            default -> "other";
        };
    }
}
