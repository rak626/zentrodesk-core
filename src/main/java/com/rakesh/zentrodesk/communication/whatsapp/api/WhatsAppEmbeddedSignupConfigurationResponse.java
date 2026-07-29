package com.rakesh.zentrodesk.communication.whatsapp.api;

public record WhatsAppEmbeddedSignupConfigurationResponse(
        String appId,
        String configurationId,
        String graphApiVersion
) {
}
