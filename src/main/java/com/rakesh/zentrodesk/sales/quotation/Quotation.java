package com.rakesh.zentrodesk.sales.quotation;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
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
        name = "quotations",
        indexes = {
                @Index(name = "idx_quotation_tenant", columnList = "tenant_id"),
                @Index(name = "idx_quotation_customer", columnList = "customer_id"),
                @Index(name = "idx_quotation_lead", columnList = "lead_id"),
                @Index(name = "idx_quotation_number", columnList = "quotation_number"),
                @Index(name = "idx_quotation_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_quotation_tenant_number",
                        columnNames = {"tenant_id", "quotation_number"}
                )
        }
)
public class Quotation extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "customer_id")
    private UUID customerId;

    @NotNull
    @Column(name = "quotation_number", nullable = false, length = 30)
    private String quotationNumber;

    @Column(name = "travel_start")
    private LocalDate travelStart;

    @Column(name = "travel_end")
    private LocalDate travelEnd;

    private Integer adults;

    private Integer children;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuotationStatus status;

    @Size(max = 1000)
    @Column(length = 1000)
    private String notes;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
