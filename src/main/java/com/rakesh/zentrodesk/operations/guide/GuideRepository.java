package com.rakesh.zentrodesk.operations.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuideRepository extends JpaRepository<Guide, UUID>, JpaSpecificationExecutor<Guide> {

    Optional<Guide> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Guide> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
}
