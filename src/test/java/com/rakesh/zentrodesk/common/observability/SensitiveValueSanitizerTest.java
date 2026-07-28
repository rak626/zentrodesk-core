package com.rakesh.zentrodesk.common.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveValueSanitizerTest {

    @Test
    void redactsCredentialsAndCustomerFields() {
        String sanitized = SensitiveValueSanitizer.sanitize(
                "authorization=Bearer secret-token, email=user@example.test; phone=9999999999"
        );

        assertFalse(sanitized.contains("secret-token"));
        assertFalse(sanitized.contains("user@example.test"));
        assertFalse(sanitized.contains("9999999999"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }
}
