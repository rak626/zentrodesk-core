package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionMethod;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppFundingStatus;

import java.time.Instant;

public record WhatsAppConfigurationResponse(
        boolean configured,
        String wabaId,
        String businessId,
        String phoneNumberId,
        String displayPhoneNumber,
        String verifiedName,
        WhatsAppConnectionMethod connectionMethod,
        WhatsAppFundingStatus fundingStatus,
        boolean tokenHealthy,
        WhatsAppConnectionStatus status,
        Instant tokenExpiresAt,
        Instant lastValidatedAt,
        Instant lastHealthyAt,
        String lastError
) {
}
