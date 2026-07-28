package com.rakesh.zentrodesk.operations.driver;

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

import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "drivers",
        indexes = {
                @Index(name = "idx_driver_tenant", columnList = "tenant_id"),
                @Index(name = "idx_driver_phone", columnList = "phone"),
                @Index(name = "idx_driver_status", columnList = "status"),
                @Index(name = "idx_driver_tenant_vehicle", columnList = "tenant_id, vehicle_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_driver_id_tenant", columnNames = {"id", "tenant_id"})
        }
)
public class Driver extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

    @Size(max = 100)
    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Size(max = 100)
    @Column(name = "vehicle_number", length = 100)
    private String vehicleNumber;

    @Size(max = 100)
    @Column(name = "vehicle_type", length = 100)
    private String vehicleType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DriverStatus status;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
