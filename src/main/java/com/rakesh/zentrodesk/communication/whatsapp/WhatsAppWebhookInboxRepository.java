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

interface WhatsAppWebhookInboxRepository extends JpaRepository<WhatsAppWebhookInbox, UUID> {

    boolean existsByPayloadHash(String payloadHash);

    List<WhatsAppWebhookInbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            WhatsAppWebhookStatus status,
            Instant nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select webhook from WhatsAppWebhookInbox webhook where webhook.id = :id")
    Optional<WhatsAppWebhookInbox> findLockedById(@Param("id") UUID id);
}
