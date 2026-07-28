package com.rakesh.zentrodesk.operations.driver;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverStatusHistoryRepository extends JpaRepository<DriverStatusHistory, UUID> {

    Page<DriverStatusHistory> findByTenantIdAndDriverId(UUID tenantId, UUID driverId, Pageable pageable);
}
