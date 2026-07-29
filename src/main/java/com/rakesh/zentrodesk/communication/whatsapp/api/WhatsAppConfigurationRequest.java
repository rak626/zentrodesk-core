package com.rakesh.zentrodesk.communication.whatsapp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WhatsAppConfigurationRequest(
        @NotBlank @Size(max = 100) String wabaId,
        @NotBlank @Size(max = 100) String businessId,
        @NotBlank @Size(max = 100) String phoneNumberId,
        @NotBlank String accessToken,
        @NotBlank @Pattern(regexp = "\\d{6}") String registrationPin
) {
}
