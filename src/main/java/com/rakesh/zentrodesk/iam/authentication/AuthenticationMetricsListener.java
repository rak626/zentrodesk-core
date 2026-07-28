package com.rakesh.zentrodesk.iam.authentication;

import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationMetricsListener {

    private final ApplicationMetrics metrics;

    @EventListener
    public void onAuthenticationSecurityEvent(AuthenticationSecurityEvent event) {
        metrics.authentication(event.accountType().name(), event.action(), event.outcome());
        log.atInfo()
                .addKeyValue("account_type", event.accountType().name())
                .addKeyValue("action", event.action())
                .addKeyValue("outcome", event.outcome())
                .log("Authentication security event");
    }
}
