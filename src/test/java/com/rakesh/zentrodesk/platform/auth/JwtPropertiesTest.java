package com.rakesh.zentrodesk.platform.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingSecret() {
        assertFalse(validator.validate(new JwtProperties(null, null, 1, 1)).isEmpty());
    }

    @Test
    void rejectsWeakSecret() {
        assertFalse(validator.validate(new JwtProperties("too-short", null, 1, 1)).isEmpty());
    }

    @Test
    void rejectsWeakPreviousSecret() {
        assertFalse(validator.validate(new JwtProperties(
                "test-secret-with-at-least-32-characters",
                "too-short",
                1,
                1
        )).isEmpty());
    }

    @Test
    void acceptsValidSettings() {
        JwtProperties properties = new JwtProperties(
                "test-secret-with-at-least-32-characters",
                null,
                900_000,
                604_800_000
        );

        assertTrue(validator.validate(properties).isEmpty());
    }
}
