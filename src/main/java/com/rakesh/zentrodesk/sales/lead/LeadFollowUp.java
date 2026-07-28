package com.rakesh.zentrodesk.sales.lead;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
        name = "lead_follow_ups",
        indexes = {
                @Index(name = "idx_follow_up_tenant_lead", columnList = "tenant_id,lead_id"),
                @Index(name = "idx_follow_up_tenant_owner_due", columnList = "tenant_id,owner_id,due_at")
        }
)
public class LeadFollowUp extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpPriority priority;

    @Column(length = 1000)
    private String notes;

    @Column(length = 1000)
    private String outcome;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_state", nullable = false, length = 20)
    private ReminderState reminderState;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
