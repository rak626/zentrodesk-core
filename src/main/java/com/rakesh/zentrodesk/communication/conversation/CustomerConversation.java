package com.rakesh.zentrodesk.communication.conversation;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
        name = "customer_conversations",
        indexes = {
                @Index(name = "idx_customer_conversation_inbox", columnList = "tenant_id, status, last_message_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customer_conversation_tenant_phone",
                        columnNames = {"tenant_id", "normalized_phone"}
                )
        }
)
public class CustomerConversation extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(name = "normalized_phone", nullable = false, length = 32)
    private String normalizedPhone;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerConversationStatus status = CustomerConversationStatus.OPEN;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_inbound_at")
    private Instant lastInboundAt;

    @Default
    @Column(name = "unread_count", nullable = false)
    private int unreadCount = 0;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
