package com.rakesh.zentrodesk.sales.quotation;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "quotation_items",
        indexes = {
                @Index(name = "idx_qitem_quotation", columnList = "quotation_id"),
                @Index(name = "idx_qitem_type", columnList = "type")
        }
)
public class QuotationItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuotationItemType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_basis", nullable = false, length = 30)
    private QuotationPricingBasis pricingBasis;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 100)
    private String unit;

    @NotNull
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "chargeable_people")
    private Integer chargeablePeople;

    @Default
    @Column(name = "chargeable_people_overridden", nullable = false)
    private boolean chargeablePeopleOverridden = false;

    @Column(name = "vehicle_capacity")
    private Integer vehicleCapacity;

    @Column(name = "vehicle_count")
    private Integer vehicleCount;

    @Default
    @Column(name = "vehicle_count_overridden", nullable = false)
    private boolean vehicleCountOverridden = false;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "pickup_location", length = 500)
    private String pickupLocation;

    @Column(name = "dropoff_location", length = 500)
    private String dropoffLocation;

    @Default
    @Column(name = "capacity_overridden", nullable = false)
    private boolean capacityOverridden = false;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
