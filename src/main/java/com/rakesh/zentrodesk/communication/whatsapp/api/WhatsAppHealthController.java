package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/whatsapp/health")
@PreAuthorize("hasAuthority('CONVERSATION_READ')")
public class WhatsAppHealthController {

    private final WhatsAppConfigurationService service;

    @GetMapping
    public ApiResponse<WhatsAppConnectionHealthResponse> get(HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "WhatsApp connection health retrieved successfully",
                service.health(),
                servletRequest.getRequestURI()
        );
    }
}
