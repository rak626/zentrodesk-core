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
        name = "whatsapp_message_outbox",
        indexes = {
                @Index(
                        name = "idx_whatsapp_outbox_delivery",
                        columnList = "status, next_attempt_at, created_at"
                )
        }
)
public class WhatsAppMessageOutbox extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "sender_phone_number_id", nullable = false, length = 100)
    private String senderPhoneNumberId;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppOutboxStatus status = WhatsAppOutboxStatus.PENDING;

    @Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
