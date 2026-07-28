package com.rakesh.zentrodesk.operations.driver;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
        name = "vehicles",
        indexes = {
                @Index(name = "idx_vehicle_tenant_status", columnList = "tenant_id, status, active")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vehicle_tenant_registration",
                        columnNames = {"tenant_id", "registration_number"}
                ),
                @UniqueConstraint(name = "uk_vehicle_id_tenant", columnNames = {"id", "tenant_id"})
        }
)
public class Vehicle extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "registration_number", nullable = false, length = 100)
    private String registrationNumber;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String type;

    @Size(max = 100)
    @Column(length = 100)
    private String make;

    @Size(max = 100)
    @Column(length = 100)
    private String model;

    @Positive
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleStatus status;

    @Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
