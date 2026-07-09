package com.rakesh.zentrodesk.platform.role;

import com.rakesh.common.entity.BaseEntity;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "tenant_roles",
        indexes = {
                @Index(name = "idx_role_tenant", columnList = "tenant_id"),
                @Index(name = "idx_role_name", columnList = "name")
        }
)
public class Role extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 255)
    @Column(length = 255)
    private String description;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "tenant_role_permissions",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "permission", nullable = false, length = 100)
    private Set<String> permissions = new LinkedHashSet<>();
}
