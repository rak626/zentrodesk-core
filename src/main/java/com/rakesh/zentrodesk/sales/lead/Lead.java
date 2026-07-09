package com.rakesh.zentrodesk.sales.lead;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "leads",
        indexes = {
                @Index(name = "idx_lead_tenant", columnList = "tenant_id"),
                @Index(name = "idx_lead_assigned_user", columnList = "assigned_user_id"),
                @Index(name = "idx_lead_phone", columnList = "phone"),
                @Index(name = "idx_lead_status", columnList = "status")
        }
)
public class Lead extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

    @Email
    @Size(max = 150)
    @Column(length = 150)
    private String email;

    @Size(max = 100)
    @Column(length = 100)
    private String source;

    @Size(max = 150)
    @Column(length = 150)
    private String destination;

    @Column(name = "travel_date")
    private LocalDate travelDate;

    private Integer adults;

    private Integer children;

    @Column(precision = 12, scale = 2)
    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadStatus status;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
