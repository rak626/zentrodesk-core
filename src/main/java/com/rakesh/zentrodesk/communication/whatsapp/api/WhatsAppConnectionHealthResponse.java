package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppFundingStatus;

import java.time.Instant;

public record WhatsAppConnectionHealthResponse(
        boolean available,
        WhatsAppConnectionStatus status,
        WhatsAppFundingStatus fundingStatus,
        boolean tokenHealthy,
        String displayPhoneNumber,
        String verifiedName,
        Instant tokenExpiresAt,
        Instant lastValidatedAt,
        String error
) {
}
