package com.rakesh.zentrodesk.communication.notification;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "notifications",
        indexes = {
                @Index(name = "idx_notification_user", columnList = "user_id"),
                @Index(name = "idx_notification_tenant_user", columnList = "tenant_id, user_id"),
                @Index(name = "idx_notification_status", columnList = "status"),
                @Index(
                        name = "idx_notification_recipient_unread",
                        columnList = "tenant_id, user_id, status, active, created_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_recipient_event",
                        columnNames = {"tenant_id", "user_id", "event_key"}
                )
        }
)
public class Notification extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String category;

    @Size(max = 300)
    @Column(name = "deep_link", length = 300)
    private String deepLink;

    @NotBlank
    @Size(max = 180)
    @Column(name = "event_key", nullable = false, length = 180)
    private String eventKey;

    @Size(max = 50)
    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
