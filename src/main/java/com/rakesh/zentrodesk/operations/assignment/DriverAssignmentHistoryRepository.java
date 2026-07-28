package com.rakesh.zentrodesk.operations.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverAssignmentHistoryRepository extends JpaRepository<DriverAssignmentHistory, UUID> {

    Page<DriverAssignmentHistory> findByTenantIdAndAssignmentId(
            UUID tenantId,
            UUID assignmentId,
            Pageable pageable
    );
}
