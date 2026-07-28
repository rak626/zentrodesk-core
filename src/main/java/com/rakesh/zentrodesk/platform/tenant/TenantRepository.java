package com.rakesh.zentrodesk.platform.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    Optional<Tenant> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select count(t) > 0 from Tenant t
            where lower(t.code) = lower(:code)
              and (:excludedTenantId is null or t.id <> :excludedTenantId)
            """)
    boolean existsByCodeExcludingTenant(@Param("code") String code, @Param("excludedTenantId") UUID excludedTenantId);
}
