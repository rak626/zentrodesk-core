package com.rakesh.zentrodesk.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public final class CorrelationIds {

    private CorrelationIds() {
    }

    public static String currentOrGenerated() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
            if (value instanceof String correlationId && !correlationId.isBlank()) {
                return correlationId;
            }
        }
        return UUID.randomUUID().toString();
    }
}
