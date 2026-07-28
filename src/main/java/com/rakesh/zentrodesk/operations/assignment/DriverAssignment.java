package com.rakesh.zentrodesk.operations.assignment;

import com.rakesh.common.entity.BaseEntity;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.driver.Driver;
import com.rakesh.zentrodesk.operations.driver.Vehicle;
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
import jakarta.persistence.Version;
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
        name = "driver_assignments",
        indexes = {
                @Index(name = "idx_assignment_tenant_pickup", columnList = "tenant_id, pickup_at, status"),
                @Index(name = "idx_assignment_tenant_driver", columnList = "tenant_id, driver_id, pickup_at"),
                @Index(name = "idx_assignment_tenant_vehicle", columnList = "tenant_id, vehicle_id, pickup_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_assignment_tenant_idempotency",
                        columnNames = {"tenant_id", "idempotency_key"}
                )
        }
)
public class DriverAssignment extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "dropoff_at", nullable = false)
    private Instant dropoffAt;

    @NotBlank
    @Size(max = 500)
    @Column(name = "pickup_location", nullable = false, length = 500)
    private String pickupLocation;

    @NotBlank
    @Size(max = 500)
    @Column(name = "dropoff_location", nullable = false, length = 500)
    private String dropoffLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status;

    @NotBlank
    @Size(max = 100)
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
