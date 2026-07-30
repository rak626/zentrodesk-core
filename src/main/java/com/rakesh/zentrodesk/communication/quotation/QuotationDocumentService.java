package com.rakesh.zentrodesk.communication.quotation;

import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.communication.template.Template;
import com.rakesh.zentrodesk.communication.template.TemplateRepository;
import com.rakesh.zentrodesk.communication.template.TemplateStatus;
import com.rakesh.zentrodesk.communication.template.TemplateType;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationItem;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotationDocumentService {

    private final QuotationDocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.quotation.default-whatsapp-template-name:quotation_document}")
    private String defaultWhatsAppTemplateName;

    @Value("${app.quotation.default-whatsapp-template-language:en}")
    private String defaultWhatsAppTemplateLanguage;

    @Transactional
    public QuotationDocument queueSnapshot(UUID tenantId, UUID quotationId) {
        Quotation quotation = quotationRepository.findByIdAndTenantId(quotationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Recipient recipient = recipient(quotation);
        TemplateSnapshot template = template(tenantId);
        Map<String, Object> model = model(tenant, quotation, recipient);
        QuotationDocument latest = documentRepository
                .findFirstByTenantIdAndQuotationIdOrderByDocumentVersionDesc(tenantId, quotationId)
                .orElse(null);
        if (latest != null
                && latest.getDeliveryStatus() != QuotationDocumentDeliveryStatus.SENT
                && latest.getGenerationStatus() != QuotationDocumentGenerationStatus.FAILED) {
            latest.setGenerationStatus(QuotationDocumentGenerationStatus.SUPERSEDED);
        }
        return documentRepository.save(QuotationDocument.builder()
                .tenantId(tenantId)
                .quotationId(quotationId)
                .customerId(quotation.getCustomerId())
                .documentVersion(latest == null ? 1 : latest.getDocumentVersion() + 1)
                .templateId(template.id())
                .templateVersion(template.version())
                .templateName(template.name())
                .htmlSnapshot(template.html())
                .styleSnapshot(template.css())
                .modelSnapshot(serialize(model))
                .fileName(fileName(quotation))
                .generationStatus(QuotationDocumentGenerationStatus.PENDING)
                .nextGenerationAttemptAt(Instant.now())
                .deliveryStatus(QuotationDocumentDeliveryStatus.NOT_REQUESTED)
                .recipientName(recipient.name())
                .recipientPhone(recipient.phone())
                .whatsAppTemplateName(template.whatsAppTemplateName())
                .whatsAppTemplateLanguage(template.whatsAppTemplateLanguage())
                .build());
    }

    @Transactional
    public void requestSend(UUID tenantId, UUID quotationId) {
        Quotation quotation = quotationRepository.findByIdAndTenantId(quotationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        if (quotation.getStatus() == QuotationStatus.DRAFT) {
            throw new ConflictException("Draft quotation cannot be sent");
        }
        QuotationDocument document = documentRepository
                .findFirstByTenantIdAndQuotationIdOrderByDocumentVersionDesc(tenantId, quotationId)
                .filter(item -> item.getGenerationStatus() != QuotationDocumentGenerationStatus.SUPERSEDED)
                .orElseGet(() -> queueSnapshot(tenantId, quotationId));
        if (document.getGenerationStatus() == QuotationDocumentGenerationStatus.FAILED) {
            document = queueSnapshot(tenantId, quotationId);
        }
        document.setSendRequested(true);
        if (document.getRecipientPhone() == null || document.getRecipientPhone().isBlank()) {
            document.setDeliveryStatus(QuotationDocumentDeliveryStatus.FAILED);
            document.setLastError("Quotation recipient does not have a phone number");
            return;
        }
        if (document.getWhatsAppTemplateName() == null || document.getWhatsAppTemplateName().isBlank()) {
            document.setDeliveryStatus(QuotationDocumentDeliveryStatus.FAILED);
            document.setLastError("Quotation WhatsApp utility template is not configured");
            return;
        }
        document.setDeliveryStatus(QuotationDocumentDeliveryStatus.PENDING);
        document.setLastError(null);
        if (document.getGenerationStatus() == QuotationDocumentGenerationStatus.GENERATED) {
            eventPublisher.publishEvent(new QuotationDocumentReadyEvent(document.getId(), Instant.now()));
        }
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION_DOCUMENT", action = "REGENERATE_AND_SEND")
    public QuotationDocument regenerateAndSend(UUID tenantId, UUID quotationId) {
        queueSnapshot(tenantId, quotationId);
        requestSend(tenantId, quotationId);
        return requireLatest(tenantId, quotationId);
    }

    @Transactional(readOnly = true)
    public QuotationDocument requireLatest(UUID tenantId, UUID quotationId) {
        return documentRepository
                .findFirstByTenantIdAndQuotationIdOrderByDocumentVersionDesc(tenantId, quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation document not found"));
    }

    @Transactional(readOnly = true)
    public QuotationDocument requireGenerated(UUID tenantId, UUID quotationId) {
        QuotationDocument document = requireLatest(tenantId, quotationId);
        if (document.getGenerationStatus() != QuotationDocumentGenerationStatus.GENERATED
                || document.getContent() == null) {
            throw new ResourceNotFoundException("Generated quotation PDF is not available");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public QuotationDocument requireForDelivery(UUID id) {
        QuotationDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation document not found"));
        if (document.getGenerationStatus() != QuotationDocumentGenerationStatus.GENERATED
                || document.getContent() == null) {
            throw new ResourceNotFoundException("Generated quotation PDF is not available");
        }
        return document;
    }

    @Transactional
    public void markDeliverySent(UUID id, String providerMessageId) {
        documentRepository.findById(id).ifPresent(document -> {
            document.setDeliveryStatus(QuotationDocumentDeliveryStatus.SENT);
            document.setProviderMessageId(providerMessageId);
            document.setLastError(null);
        });
    }

    @Transactional
    public void markDeliveryConfirmed(UUID id) {
        documentRepository.findById(id).ifPresent(document -> document.setDeliveredAt(Instant.now()));
    }

    @Transactional
    public void markDeliveryFailed(UUID id, String error) {
        documentRepository.findById(id).ifPresent(document -> {
            document.setDeliveryStatus(QuotationDocumentDeliveryStatus.FAILED);
            document.setLastError(truncate(error));
        });
    }

    private Map<String, Object> model(Tenant tenant, Quotation quotation, Recipient recipient) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("tenant", QuotationTemplateDefaults.map(
                "name", value(tenant.getName()),
                "address", value(tenant.getAddress()),
                "city", value(tenant.getCity()),
                "state", value(tenant.getState()),
                "country", value(tenant.getCountry()),
                "phone", value(tenant.getPhone()),
                "email", value(tenant.getEmail()),
                "currency", value(tenant.getCurrency())
        ));
        model.put("customer", QuotationTemplateDefaults.map(
                "name", value(recipient.name()),
                "phone", value(recipient.phone()),
                "email", value(recipient.email()),
                "address", value(recipient.address())
        ));
        model.put("quotation", QuotationTemplateDefaults.map(
                "number", quotation.getQuotationNumber(),
                "revisionNumber", quotation.getRevisionNumber(),
                "travelStart", value(quotation.getTravelStart()),
                "travelEnd", value(quotation.getTravelEnd()),
                "adults", value(quotation.getAdults()),
                "children", value(quotation.getChildren()),
                "subtotal", value(quotation.getSubtotal()),
                "discount", value(quotation.getDiscount()),
                "tax", value(quotation.getTax()),
                "total", value(quotation.getTotal()),
                "validUntil", value(quotation.getValidUntil()),
                "notes", value(quotation.getNotes())
        ));
        List<Map<String, Object>> items = itemRepository
                .findByQuotationIdAndActiveTrueOrderByPosition(quotation.getId())
                .stream()
                .map(this::itemModel)
                .toList();
        model.put("items", items);
        return model;
    }

    private Map<String, Object> itemModel(QuotationItem item) {
        return QuotationTemplateDefaults.map(
                "position", item.getPosition() + 1,
                "type", item.getType(),
                "pricingBasis", item.getPricingBasis(),
                "description", item.getDescription(),
                "unit", value(item.getUnit()),
                "quantity", item.getQuantity(),
                "unitPrice", item.getUnitPrice(),
                "amount", item.getAmount(),
                "checkIn", value(item.getCheckIn()),
                "checkOut", value(item.getCheckOut()),
                "startsAt", value(item.getStartsAt()),
                "endsAt", value(item.getEndsAt()),
                "pickupLocation", value(item.getPickupLocation()),
                "dropoffLocation", value(item.getDropoffLocation()),
                "remarks", value(item.getRemarks())
        );
    }

    private Recipient recipient(Quotation quotation) {
        if (quotation.getCustomerId() != null) {
            Customer customer = customerRepository.findByIdAndTenantId(
                            quotation.getCustomerId(),
                            quotation.getTenantId()
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("Quotation customer not found"));
            return new Recipient(
                    customer.getName(),
                    customer.getPhone(),
                    customer.getEmail(),
                    customer.getAddress()
            );
        }
        Lead lead = leadRepository.findByIdAndTenantId(quotation.getLeadId(), quotation.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Quotation lead not found"));
        return new Recipient(lead.getName(), lead.getPhone(), lead.getEmail(), null);
    }

    private TemplateSnapshot template(UUID tenantId) {
        return templateRepository
                .findFirstByTenantIdAndTypeAndStatusAndActiveTrueOrderByTemplateVersionDesc(
                        tenantId,
                        TemplateType.QUOTATION_PDF,
                        TemplateStatus.ACTIVE
                )
                .map(template -> new TemplateSnapshot(
                        template.getId(),
                        template.getTemplateVersion(),
                        template.getName(),
                        template.getContent(),
                        template.getStyleContent(),
                        template.getWhatsAppTemplateName(),
                        template.getWhatsAppTemplateLanguage()
                ))
                .orElseGet(() -> new TemplateSnapshot(
                        null,
                        0,
                        QuotationTemplateDefaults.NAME,
                        QuotationTemplateDefaults.HTML,
                        QuotationTemplateDefaults.STYLE,
                        defaultWhatsAppTemplateName,
                        defaultWhatsAppTemplateLanguage
                ));
    }

    private String serialize(Map<String, Object> model) {
        try {
            return objectMapper.writeValueAsString(model);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to snapshot quotation PDF data", exception);
        }
    }

    private String fileName(Quotation quotation) {
        String number = quotation.getQuotationNumber().replaceAll("[^a-zA-Z0-9_-]", "-");
        return number + "-revision-" + quotation.getRevisionNumber() + ".pdf";
    }

    private Object value(Object value) {
        return value == null ? "" : value;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    private record Recipient(String name, String phone, String email, String address) {
    }

    private record TemplateSnapshot(
            UUID id,
            int version,
            String name,
            String html,
            String css,
            String whatsAppTemplateName,
            String whatsAppTemplateLanguage
    ) {
    }
}
