package com.rakesh.zentrodesk.platform.role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {

    @EntityGraph(attributePaths = {"tenant", "permissions"})
    @Query("select r from Role r where r.id = :id")
    Optional<Role> findWithTenantAndPermissionsById(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"tenant", "permissions"})
    Page<Role> findAll(Specification<Role> specification, Pageable pageable);

    @Query("""
            select count(r) > 0 from Role r
            where lower(r.name) = lower(:name)
              and r.tenant.id = :tenantId
              and (:excludedRoleId is null or r.id <> :excludedRoleId)
            """)
    boolean existsByTenantAndNameExcludingRole(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("excludedRoleId") UUID excludedRoleId
    );
}
