package com.rakesh.zentrodesk.platform.email;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    List<EmailOutboxMessage> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            EmailOutboxStatus status,
            Instant nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from EmailOutboxMessage message where message.id = :id")
    Optional<EmailOutboxMessage> findLockedById(@Param("id") UUID id);
}
