package com.rakesh.zentrodesk.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID>, JpaSpecificationExecutor<PlatformUser> {

    Optional<PlatformUser> findByEmailIgnoreCase(String email);

    @Query("""
            select count(u) > 0 from PlatformUser u
            where lower(u.email) = lower(:email)
              and (:excludedUserId is null or u.id <> :excludedUserId)
            """)
    boolean existsByEmailExcludingUser(@Param("email") String email, @Param("excludedUserId") UUID excludedUserId);

    long countByRoleAndEnabledTrue(PlatformRole role);
}
