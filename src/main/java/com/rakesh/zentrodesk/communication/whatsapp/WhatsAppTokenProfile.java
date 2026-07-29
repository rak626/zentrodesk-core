package com.rakesh.zentrodesk.communication.whatsapp;

import java.time.Instant;
import java.util.Set;

public record WhatsAppTokenProfile(
        boolean valid,
        String appId,
        Set<String> scopes,
        Instant expiresAt
) {
}
