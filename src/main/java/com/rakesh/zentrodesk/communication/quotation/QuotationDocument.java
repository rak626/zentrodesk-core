package com.rakesh.zentrodesk.communication.quotation;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "quotation_documents",
        indexes = {
                @Index(
                        name = "idx_quotation_document_processing",
                        columnList = "generation_status, next_generation_attempt_at, created_at"
                ),
                @Index(
                        name = "idx_quotation_document_lookup",
                        columnList = "tenant_id, quotation_id, document_version"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_quotation_document_version",
                        columnNames = {"quotation_id", "document_version"}
                )
        }
)
public class QuotationDocument extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "document_version", nullable = false)
    private int documentVersion;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "html_snapshot", columnDefinition = "TEXT", nullable = false)
    private String htmlSnapshot;

    @Column(name = "style_snapshot", columnDefinition = "TEXT")
    private String styleSnapshot;

    @Column(name = "model_snapshot", columnDefinition = "TEXT", nullable = false)
    private String modelSnapshot;

    @Column(name = "file_name", nullable = false, length = 180)
    private String fileName;

    @Column(columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "content_length")
    private Long contentLength;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false, length = 20)
    private QuotationDocumentGenerationStatus generationStatus = QuotationDocumentGenerationStatus.PENDING;

    @Default
    @Column(name = "generation_attempts", nullable = false)
    private int generationAttempts = 0;

    @Column(name = "next_generation_attempt_at", nullable = false)
    private Instant nextGenerationAttemptAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Default
    @Column(name = "send_requested", nullable = false)
    private boolean sendRequested = false;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private QuotationDocumentDeliveryStatus deliveryStatus = QuotationDocumentDeliveryStatus.NOT_REQUESTED;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "whatsapp_template_name", length = 512)
    private String whatsAppTemplateName;

    @Column(name = "whatsapp_template_language", length = 10)
    private String whatsAppTemplateLanguage;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
