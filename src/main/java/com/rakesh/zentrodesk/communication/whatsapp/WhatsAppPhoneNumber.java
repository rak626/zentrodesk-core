package com.rakesh.zentrodesk.communication.whatsapp;

public record WhatsAppPhoneNumber(
        String id,
        String displayPhoneNumber,
        String verifiedName,
        String qualityRating
) {
}
