package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
        name = "whatsapp_webhook_inbox",
        indexes = {
                @Index(
                        name = "idx_whatsapp_webhook_processing",
                        columnList = "status, next_attempt_at, created_at"
                )
        }
)
public class WhatsAppWebhookInbox extends BaseEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "phone_number_id", length = 100)
    private String phoneNumberId;

    @Column(name = "payload_hash", nullable = false, unique = true, length = 64)
    private String payloadHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppWebhookStatus status = WhatsAppWebhookStatus.PENDING;

    @Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
