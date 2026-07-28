package com.rakesh.zentrodesk.operations.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, TaskStatus status);

    Optional<Task> findByTenantIdAndSourceTypeAndSourceIdAndActiveTrue(
            UUID tenantId,
            String sourceType,
            UUID sourceId
    );
}
