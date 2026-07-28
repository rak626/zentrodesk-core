package com.rakesh.zentrodesk.platform.entitlement;

import com.rakesh.common.entity.BaseEntity;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "tenant_entitlements",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_entitlement_feature",
                        columnNames = {"tenant_id", "feature_key"}
                )
        },
        indexes = {
                @Index(name = "idx_tenant_entitlement_tenant", columnList = "tenant_id, feature_key")
        }
)
public class TenantEntitlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(nullable = false)
    private boolean enabled;
}
