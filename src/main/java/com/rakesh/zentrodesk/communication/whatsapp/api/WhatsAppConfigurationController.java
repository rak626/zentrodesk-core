package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppPhoneNumber;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/whatsapp/configuration")
@PreAuthorize("hasRole('OWNER')")
public class WhatsAppConfigurationController {

    private final WhatsAppConfigurationService service;

    @GetMapping
    public ApiResponse<WhatsAppConfigurationResponse> get(HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "WhatsApp configuration retrieved successfully",
                service.get(),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/embedded-signup")
    public ApiResponse<WhatsAppEmbeddedSignupConfigurationResponse> embeddedSignup(
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Embedded Signup configuration retrieved successfully",
                service.embeddedSignupConfiguration(),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/embedded-signup/complete")
    public ApiResponse<WhatsAppConfigurationResponse> completeEmbeddedSignup(
            @Valid @RequestBody WhatsAppEmbeddedSignupRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "WhatsApp Embedded Signup completed successfully",
                service.completeEmbeddedSignup(request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/phone-numbers")
    public ApiResponse<List<WhatsAppPhoneNumber>> discover(
            @Valid @RequestBody WhatsAppDiscoveryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "WhatsApp phone numbers retrieved successfully",
                service.discover(request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping
    public ApiResponse<WhatsAppConfigurationResponse> save(
            @Valid @RequestBody WhatsAppConfigurationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "WhatsApp configuration saved successfully",
                service.save(request),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/replace")
    public ApiResponse<WhatsAppConfigurationResponse> replace(
            @Valid @RequestBody WhatsAppConfigurationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "WhatsApp connection replaced successfully",
                service.save(request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/validate")
    public ApiResponse<WhatsAppConfigurationResponse> validate(HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "WhatsApp configuration validation completed",
                service.validate(),
                servletRequest.getRequestURI()
        );
    }

    @DeleteMapping
    public ApiResponse<WhatsAppConfigurationResponse> disconnect(HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "WhatsApp connection disconnected successfully",
                service.disconnect(),
                servletRequest.getRequestURI()
        );
    }
}
