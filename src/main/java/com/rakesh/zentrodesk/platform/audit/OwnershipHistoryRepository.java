package com.rakesh.zentrodesk.platform.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface OwnershipHistoryRepository extends
        JpaRepository<OwnershipHistory, UUID>,
        JpaSpecificationExecutor<OwnershipHistory> {
}
