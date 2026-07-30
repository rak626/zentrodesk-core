package com.rakesh.zentrodesk.communication.template.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/quotation-templates")
@PreAuthorize("hasRole('OWNER')")
public class QuotationTemplateController {

    private final QuotationTemplateService templateService;

    @GetMapping("/current")
    public ApiResponse<QuotationTemplateResponse> current(HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "Quotation template retrieved successfully",
                templateService.current(),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/draft")
    public ApiResponse<QuotationTemplateResponse> draft(HttpServletRequest servletRequest) {
        return ApiResponse.success(
                "Quotation template draft retrieved successfully",
                templateService.draft(),
                servletRequest.getRequestURI()
        );
    }

    @PutMapping("/draft")
    public ApiResponse<QuotationTemplateResponse> saveDraft(
            @Valid @RequestBody QuotationTemplateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation template draft saved successfully",
                templateService.saveDraft(request),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<QuotationTemplateResponse> publish(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Quotation template published successfully",
                templateService.publish(id),
                servletRequest.getRequestURI()
        );
    }

    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@Valid @RequestBody QuotationTemplateRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("quotation-template-preview.pdf").build().toString()
                )
                .body(templateService.preview(request));
    }
}
