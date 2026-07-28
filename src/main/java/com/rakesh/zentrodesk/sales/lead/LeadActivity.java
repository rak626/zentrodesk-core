package com.rakesh.zentrodesk.sales.lead;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
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
        name = "lead_activities",
        indexes = @Index(name = "idx_lead_activity_tenant_lead_created", columnList = "tenant_id,lead_id,created_at")
)
public class LeadActivity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "activity_type", nullable = false, length = 40)
    private String activityType;

    @Column(nullable = false, length = 500)
    private String summary;
}
