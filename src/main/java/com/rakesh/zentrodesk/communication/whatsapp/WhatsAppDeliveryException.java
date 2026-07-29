package com.rakesh.zentrodesk.communication.whatsapp;

class WhatsAppDeliveryException extends RuntimeException {

    private final boolean retryable;

    WhatsAppDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    WhatsAppDeliveryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean isRetryable() {
        return retryable;
    }
}
