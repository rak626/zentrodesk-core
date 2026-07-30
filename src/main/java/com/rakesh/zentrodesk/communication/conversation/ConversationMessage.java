package com.rakesh.zentrodesk.communication.conversation;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "conversation_messages",
        indexes = {
                @Index(name = "idx_conversation_tenant", columnList = "tenant_id"),
                @Index(name = "idx_conversation_customer", columnList = "customer_id"),
                @Index(name = "idx_conversation_status", columnList = "status"),
                @Index(name = "idx_conversation_message_thread", columnList = "conversation_id, created_at")
        }
)
public class ConversationMessage extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "sent_by_user_id")
    private UUID sentByUserId;

    @Column(name = "business_phone_number_id", length = 100)
    private String businessPhoneNumberId;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationMessageDirection direction;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ConversationMessageType messageType = ConversationMessageType.TEXT;

    @Size(max = 100)
    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_file_name", length = 180)
    private String documentFileName;

    @Column(name = "template_name", length = 512)
    private String templateName;

    @Column(name = "template_language", length = 10)
    private String templateLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationMessageStatus status;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
