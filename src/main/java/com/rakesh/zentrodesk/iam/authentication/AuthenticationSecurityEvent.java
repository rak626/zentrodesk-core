package com.rakesh.zentrodesk.iam.authentication;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationSecurityEvent(
        Instant occurredAt,
        AccountType accountType,
        UUID accountId,
        String action,
        String outcome
) {
}
