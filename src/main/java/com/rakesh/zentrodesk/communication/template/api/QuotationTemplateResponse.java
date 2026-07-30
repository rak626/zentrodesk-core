package com.rakesh.zentrodesk.communication.template.api;

import com.rakesh.zentrodesk.communication.template.TemplateStatus;

import java.time.Instant;
import java.util.UUID;

public record QuotationTemplateResponse(
        UUID id,
        String name,
        String html,
        String css,
        String whatsAppTemplateName,
        String whatsAppTemplateLanguage,
        int version,
        TemplateStatus status,
        boolean systemDefault,
        Instant publishedAt,
        Instant updatedAt
) {
}
