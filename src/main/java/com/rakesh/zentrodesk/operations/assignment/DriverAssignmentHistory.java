package com.rakesh.zentrodesk.operations.assignment;

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
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Immutable
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "driver_assignment_history",
        indexes = {
                @Index(
                        name = "idx_assignment_history_tenant_assignment_created",
                        columnList = "tenant_id, assignment_id, created_at"
                )
        }
)
public class DriverAssignmentHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private DriverAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentAction action;

    @Column(name = "previous_driver_id")
    private UUID previousDriverId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "previous_vehicle_id")
    private UUID previousVehicleId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private AssignmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;
}
