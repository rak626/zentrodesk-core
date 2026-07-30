package com.rakesh.zentrodesk.platform.audit;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "ownership_history",
        indexes = {
                @Index(name = "idx_ownership_history_tenant_time", columnList = "tenant_id, occurred_at"),
                @Index(name = "idx_ownership_history_lead", columnList = "tenant_id, lead_id, occurred_at"),
                @Index(name = "idx_ownership_history_quotation", columnList = "tenant_id, quotation_id, occurred_at"),
                @Index(name = "idx_ownership_history_booking", columnList = "tenant_id, booking_id, occurred_at"),
                @Index(name = "idx_ownership_history_conversation", columnList = "tenant_id, conversation_id, occurred_at")
        }
)
public class OwnershipHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "previous_owner_id")
    private UUID previousOwnerId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 80)
    private String reason;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "quotation_id")
    private UUID quotationId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
