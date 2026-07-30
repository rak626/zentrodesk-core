package com.rakesh.zentrodesk.communication.quotation.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocument;
import com.rakesh.zentrodesk.communication.quotation.QuotationDocumentService;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/quotations/{quotationId}/document")
@PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_READ + "')")
public class QuotationDocumentController {

    private final QuotationDocumentService documentService;
    private final CurrentTenant currentTenant;

    @GetMapping
    public ApiResponse<QuotationDocumentResponse> status(
            @PathVariable UUID quotationId,
            HttpServletRequest servletRequest
    ) {
        QuotationDocument document = documentService.requireLatest(currentTenant.id(), quotationId);
        return ApiResponse.success(
                "Quotation document retrieved successfully",
                toResponse(document),
                servletRequest.getRequestURI()
        );
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> download(@PathVariable UUID quotationId) {
        QuotationDocument document = documentService.requireGenerated(currentTenant.id(), quotationId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.getContentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getFileName()).build().toString()
                )
                .body(document.getContent());
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('" + TenantPermissions.QUOTATION_UPDATE + "')")
    public ApiResponse<QuotationDocumentResponse> regenerateAndSend(
            @PathVariable UUID quotationId,
            HttpServletRequest servletRequest
    ) {
        QuotationDocument document = documentService.regenerateAndSend(currentTenant.id(), quotationId);
        return ApiResponse.success(
                "Quotation document regeneration and delivery queued successfully",
                toResponse(document),
                servletRequest.getRequestURI()
        );
    }

    private QuotationDocumentResponse toResponse(QuotationDocument document) {
        return new QuotationDocumentResponse(
                document.getId(),
                document.getQuotationId(),
                document.getDocumentVersion(),
                document.getTemplateVersion(),
                document.getTemplateName(),
                document.getFileName(),
                document.getContentLength(),
                document.getContentSha256(),
                document.getGenerationStatus(),
                document.getDeliveryStatus(),
                document.getProviderMessageId(),
                document.getLastError(),
                document.getGeneratedAt(),
                document.getDeliveredAt(),
                document.getCreatedAt()
        );
    }
}
