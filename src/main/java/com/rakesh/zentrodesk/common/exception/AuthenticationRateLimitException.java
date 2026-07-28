package com.rakesh.zentrodesk.common.exception;

public class AuthenticationRateLimitException extends RuntimeException {

    public AuthenticationRateLimitException() {
        super("Authentication temporarily unavailable");
    }
}
