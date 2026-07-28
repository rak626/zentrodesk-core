package com.rakesh.zentrodesk.operations.booking;

import com.rakesh.common.entity.BaseEntity;
import com.rakesh.zentrodesk.operations.driver.Driver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_booking_tenant", columnList = "tenant_id"),
                @Index(name = "idx_booking_customer", columnList = "customer_id"),
                @Index(name = "idx_booking_driver", columnList = "driver_id"),
                @Index(name = "idx_booking_status", columnList = "status"),
                @Index(name = "idx_booking_start", columnList = "travel_start")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_booking_number",
                        columnNames = "booking_number"
                )
        }
)
public class Booking extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "quotation_id")
    private UUID quotationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @NotBlank
    @Column(name = "booking_number", nullable = false, length = 30)
    private String bookingNumber;

    @Column(name = "travel_start")
    private LocalDate travelStart;

    @Column(name = "travel_end")
    private LocalDate travelEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "agreed_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedValue;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
