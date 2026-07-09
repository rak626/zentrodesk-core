package com.rakesh.zentrodesk.marketing.campaign;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        name = "campaigns",
        indexes = {
                @Index(name = "idx_campaign_tenant", columnList = "tenant_id"),
                @Index(name = "idx_campaign_type", columnList = "type"),
                @Index(name = "idx_campaign_status", columnList = "status"),
                @Index(name = "idx_campaign_scheduled", columnList = "scheduled_at")
        }
)
public class Campaign extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignType type;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
