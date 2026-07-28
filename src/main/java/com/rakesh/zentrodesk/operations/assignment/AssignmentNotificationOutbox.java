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
        name = "assignment_notification_outbox",
        indexes = {
                @Index(
                        name = "idx_assignment_notification_delivery",
                        columnList = "status, next_attempt_at, created_at"
                )
        }
)
class AssignmentNotificationOutbox extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private DriverAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentNotificationAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AssignmentAction eventType;

    @Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentNotificationStatus status = AssignmentNotificationStatus.PENDING;

    @Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
