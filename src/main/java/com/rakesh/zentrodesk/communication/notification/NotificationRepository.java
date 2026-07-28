package com.rakesh.zentrodesk.communication.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    Optional<Notification> findByTenantIdAndUserIdAndEventKey(UUID tenantId, UUID userId, String eventKey);

    long countByTenantIdAndUserIdAndStatusAndActiveTrue(UUID tenantId, UUID userId, NotificationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
            set notification.status = com.rakesh.zentrodesk.communication.notification.NotificationStatus.READ
            where notification.tenantId = :tenantId
              and notification.userId = :userId
              and notification.status = com.rakesh.zentrodesk.communication.notification.NotificationStatus.UNREAD
              and notification.active = true
            """)
    int markAllRead(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
