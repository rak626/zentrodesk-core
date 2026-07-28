package com.rakesh.zentrodesk.platform.invitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID>,
        JpaSpecificationExecutor<TenantInvitation> {

    @EntityGraph(attributePaths = {"user", "role"})
    Page<TenantInvitation> findAllByTenantId(UUID tenantId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "role"})
    Page<TenantInvitation> findAll(Specification<TenantInvitation> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant", "user", "role"})
    @Query("""
            select invitation from TenantInvitation invitation
            where invitation.id = :id and invitation.tenant.id = :tenantId
            """)
    Optional<TenantInvitation> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant", "user", "role"})
    @Query("select invitation from TenantInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<TenantInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @EntityGraph(attributePaths = {"tenant", "user", "role"})
    Optional<TenantInvitation> findByTokenHash(String tokenHash);

    List<TenantInvitation> findAllByUserIdAndStatus(UUID userId, InvitationStatus status);
}
