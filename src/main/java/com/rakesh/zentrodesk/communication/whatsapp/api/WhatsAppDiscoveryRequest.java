package com.rakesh.zentrodesk.communication.whatsapp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WhatsAppDiscoveryRequest(
        @NotBlank @Size(max = 100) String wabaId,
        @NotBlank String accessToken
) {
}
