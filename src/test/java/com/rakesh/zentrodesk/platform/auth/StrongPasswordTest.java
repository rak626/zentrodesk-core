package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.platform.user.PasswordResetRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsLaunchPasswordPolicy() {
        assertTrue(validator.validate(new PasswordResetRequest("SecurePass1!")).isEmpty());
    }

    @Test
    void rejectsShortOrLowComplexityPasswords() {
        assertFalse(validator.validate(new PasswordResetRequest("Short1!a")).isEmpty());
        assertFalse(validator.validate(new PasswordResetRequest("longlowercase1!")).isEmpty());
        assertFalse(validator.validate(new PasswordResetRequest("LONGUPPERCASE1!")).isEmpty());
        assertFalse(validator.validate(new PasswordResetRequest("LongPassword!!")).isEmpty());
        assertFalse(validator.validate(new PasswordResetRequest("LongPassword12")).isEmpty());
    }
}
