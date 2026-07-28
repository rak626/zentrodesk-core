package com.rakesh.zentrodesk.operations.assignment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AssignmentNotificationOutboxRepository extends JpaRepository<AssignmentNotificationOutbox, UUID> {

    List<AssignmentNotificationOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            AssignmentNotificationStatus status,
            Instant nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from AssignmentNotificationOutbox message where message.id = :id")
    Optional<AssignmentNotificationOutbox> findLockedById(@Param("id") UUID id);
}
