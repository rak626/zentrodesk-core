package com.rakesh.zentrodesk.communication.whatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WhatsAppMessageOutboxRepository extends JpaRepository<WhatsAppMessageOutbox, UUID> {

    List<WhatsAppMessageOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            WhatsAppOutboxStatus status,
            Instant nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from WhatsAppMessageOutbox message where message.id = :id")
    Optional<WhatsAppMessageOutbox> findLockedById(@Param("id") UUID id);

    List<WhatsAppMessageOutbox> findAllByTenantIdAndSenderPhoneNumberIdAndStatus(
            UUID tenantId,
            String senderPhoneNumberId,
            WhatsAppOutboxStatus status
    );
}
