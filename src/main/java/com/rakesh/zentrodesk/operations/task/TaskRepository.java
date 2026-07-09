package com.rakesh.zentrodesk.operations.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    long countByTenantIdAndStatusAndActiveTrue(UUID tenantId, TaskStatus status);
}
