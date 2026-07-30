package com.rakesh.zentrodesk.communication.template.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuotationTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200000) String html,
        @Size(max = 100000) String css,
        @NotBlank
        @Size(max = 512)
        @Pattern(regexp = "[a-z0-9_]+", message = "must contain lowercase letters, numbers, and underscores")
        String whatsAppTemplateName,
        @NotBlank @Size(max = 10) String whatsAppTemplateLanguage
) {
}
