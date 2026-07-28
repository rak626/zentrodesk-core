package com.rakesh.zentrodesk.common.exception;

public class SearchRateLimitException extends RuntimeException {

    public SearchRateLimitException() {
        super("Search rate limit exceeded");
    }
}
