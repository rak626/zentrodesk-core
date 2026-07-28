package com.rakesh.zentrodesk.platform.user;

import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"tenant", "role", "role.permissions"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithTenantRoleAndPermissionsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"tenant", "role", "role.permissions"})
    @Query("select u from User u where u.id = :id and u.tenant.id = :tenantId")
    Optional<User> findWithTenantRoleAndPermissionsByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    @Query("select count(u) > 0 from User u where u.id = :id and u.tenant.id = :tenantId")
    boolean existsByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "role", "role.permissions"})
    @Query("""
            select u from User u
            where lower(u.email) = lower(:email)
              and lower(u.tenant.code) = lower(:tenantCode)
            """)
    Optional<User> findWithTenantRoleAndPermissionsByEmailAndTenantCode(
            @Param("email") String email,
            @Param("tenantCode") String tenantCode
    );

    @EntityGraph(attributePaths = {"tenant", "role", "role.permissions"})
    @Query("select u from User u where lower(u.email) = lower(:email)")
    List<User> findAllWithTenantRoleAndPermissionsByEmail(@Param("email") String email);

    @Override
    @EntityGraph(attributePaths = {"tenant", "role"})
    Page<User> findAll(Specification<User> specification, Pageable pageable);

    @Query("""
            select count(u) > 0 from User u
            where lower(u.email) = lower(:email)
              and u.tenant.id = :tenantId
              and (:excludedUserId is null or u.id <> :excludedUserId)
            """)
    boolean existsByTenantAndEmailExcludingUser(
            @Param("tenantId") UUID tenantId,
            @Param("email") String email,
            @Param("excludedUserId") UUID excludedUserId
    );

    @Query("""
            select count(u) from User u
            where u.tenant.id = :tenantId
              and (
                u.active = true
                or exists (
                    select invitation.id from TenantInvitation invitation
                    where invitation.user = u
                      and invitation.status = com.rakesh.zentrodesk.platform.invitation.InvitationStatus.PENDING
                      and invitation.expiresAt > :now
                )
              )
            """)
    long countOccupiedSeats(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    long countByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndRoleTemplateCodeAndActiveTrue(UUID tenantId, FixedTenantRole templateCode);

    @Query("""
            select u.id from User u
            where u.tenant.id = :tenantId
              and u.role.templateCode in (
                com.rakesh.zentrodesk.platform.role.FixedTenantRole.OWNER,
                com.rakesh.zentrodesk.platform.role.FixedTenantRole.MANAGER,
                com.rakesh.zentrodesk.platform.role.FixedTenantRole.OPERATIONS
              )
              and u.active = true
            """)
    List<UUID> findActiveOperationsNotificationRecipientIds(@Param("tenantId") UUID tenantId);

    @Query("""
            select u.id from User u
            where u.tenant.id = :tenantId
              and u.role.templateCode in (
                com.rakesh.zentrodesk.platform.role.FixedTenantRole.OWNER,
                com.rakesh.zentrodesk.platform.role.FixedTenantRole.MANAGER,
                com.rakesh.zentrodesk.platform.role.FixedTenantRole.ACCOUNTS
              )
              and u.active = true
            """)
    List<UUID> findActivePaymentNotificationRecipientIds(@Param("tenantId") UUID tenantId);
}
