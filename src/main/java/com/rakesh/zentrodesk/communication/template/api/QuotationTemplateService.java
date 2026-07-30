package com.rakesh.zentrodesk.communication.template.api;

import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.communication.quotation.QuotationPdfRenderer;
import com.rakesh.zentrodesk.communication.quotation.QuotationTemplateDefaults;
import com.rakesh.zentrodesk.communication.template.Template;
import com.rakesh.zentrodesk.communication.template.TemplateRepository;
import com.rakesh.zentrodesk.communication.template.TemplateStatus;
import com.rakesh.zentrodesk.communication.template.TemplateType;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotationTemplateService {

    private final TemplateRepository templateRepository;
    private final CurrentTenant currentTenant;
    private final QuotationPdfRenderer pdfRenderer;

    @Value("${app.quotation.default-whatsapp-template-name:quotation_document}")
    private String defaultWhatsAppTemplateName;

    @Value("${app.quotation.default-whatsapp-template-language:en}")
    private String defaultWhatsAppTemplateLanguage;

    @Transactional(readOnly = true)
    public QuotationTemplateResponse current() {
        return templateRepository
                .findFirstByTenantIdAndTypeAndStatusAndActiveTrueOrderByTemplateVersionDesc(
                        currentTenant.id(),
                        TemplateType.QUOTATION_PDF,
                        TemplateStatus.ACTIVE
                )
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Transactional(readOnly = true)
    public QuotationTemplateResponse draft() {
        return templateRepository
                .findFirstByTenantIdAndTypeAndStatusAndActiveTrueOrderByTemplateVersionDesc(
                        currentTenant.id(),
                        TemplateType.QUOTATION_PDF,
                        TemplateStatus.DRAFT
                )
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation template draft not found"));
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION_TEMPLATE", action = "SAVE_DRAFT")
    public QuotationTemplateResponse saveDraft(QuotationTemplateRequest request) {
        pdfRenderer.validate(request.html(), request.css());
        UUID tenantId = currentTenant.id();
        Template draft = templateRepository
                .findFirstByTenantIdAndTypeAndStatusAndActiveTrueOrderByTemplateVersionDesc(
                        tenantId,
                        TemplateType.QUOTATION_PDF,
                        TemplateStatus.DRAFT
                )
                .orElseGet(() -> Template.builder()
                        .tenantId(tenantId)
                        .type(TemplateType.QUOTATION_PDF)
                        .status(TemplateStatus.DRAFT)
                        .templateVersion(templateRepository.countByTenantIdAndType(
                                tenantId,
                                TemplateType.QUOTATION_PDF
                        ) + 1)
                        .active(true)
                        .build());
        apply(draft, request);
        return toResponse(templateRepository.save(draft));
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION_TEMPLATE", action = "PUBLISH")
    public QuotationTemplateResponse publish(UUID id) {
        UUID tenantId = currentTenant.id();
        Template template = templateRepository.findByIdAndTenantIdAndType(
                        id,
                        tenantId,
                        TemplateType.QUOTATION_PDF
                )
                .orElseThrow(() -> new ResourceNotFoundException("Quotation template not found"));
        if (template.getStatus() != TemplateStatus.DRAFT) {
            throw new ConflictException("Only a draft quotation template can be published");
        }
        pdfRenderer.validate(template.getContent(), template.getStyleContent());
        templateRepository.findAllByTenantIdAndTypeAndStatusAndActiveTrue(
                tenantId,
                TemplateType.QUOTATION_PDF,
                TemplateStatus.ACTIVE
        ).forEach(active -> {
            active.setStatus(TemplateStatus.ARCHIVED);
            active.setActive(false);
        });
        template.setStatus(TemplateStatus.ACTIVE);
        template.setPublishedAt(Instant.now());
        template.setActive(true);
        return toResponse(template);
    }

    public byte[] preview(QuotationTemplateRequest request) {
        return pdfRenderer.render(
                request.html(),
                request.css(),
                QuotationTemplateDefaults.sampleModel()
        );
    }

    private void apply(Template template, QuotationTemplateRequest request) {
        template.setName(request.name().trim());
        template.setContent(request.html().trim());
        template.setStyleContent(trimToNull(request.css()));
        template.setWhatsAppTemplateName(request.whatsAppTemplateName().trim());
        template.setWhatsAppTemplateLanguage(request.whatsAppTemplateLanguage().trim());
    }

    private QuotationTemplateResponse defaultResponse() {
        return new QuotationTemplateResponse(
                null,
                QuotationTemplateDefaults.NAME,
                QuotationTemplateDefaults.HTML,
                QuotationTemplateDefaults.STYLE,
                defaultWhatsAppTemplateName,
                defaultWhatsAppTemplateLanguage,
                0,
                TemplateStatus.ACTIVE,
                true,
                null,
                null
        );
    }

    private QuotationTemplateResponse toResponse(Template template) {
        return new QuotationTemplateResponse(
                template.getId(),
                template.getName(),
                template.getContent(),
                template.getStyleContent(),
                template.getWhatsAppTemplateName(),
                template.getWhatsAppTemplateLanguage(),
                template.getTemplateVersion(),
                template.getStatus(),
                false,
                template.getPublishedAt(),
                template.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
