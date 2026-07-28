package com.rakesh.zentrodesk.iam.refresh;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRefreshTokenRepository extends JpaRepository<AccountRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountRefreshToken token where token.tokenHash = :tokenHash")
    Optional<AccountRefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountRefreshToken token
            set token.revoked = true, token.revokedAt = :revokedAt, token.revokedReason = :reason
            where token.familyId = :familyId and token.revoked = false
            """)
    int revokeActiveFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") RefreshTokenRevocationReason reason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountRefreshToken token
            set token.revoked = true, token.revokedAt = :revokedAt, token.revokedReason = :reason
            where token.accountType = :accountType and token.accountId = :accountId and token.revoked = false
            """)
    int revokeActiveAccount(
            @Param("accountType") AccountType accountType,
            @Param("accountId") UUID accountId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") RefreshTokenRevocationReason reason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountRefreshToken token
            set token.revoked = true, token.revokedAt = :revokedAt, token.revokedReason = :reason
            where token.accountType = :accountType
              and token.accountId in (
                  select user.id from User user where user.tenant.id = :tenantId
              )
              and token.revoked = false
            """)
    int revokeActiveTenantAccounts(
            @Param("accountType") AccountType accountType,
            @Param("tenantId") UUID tenantId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") RefreshTokenRevocationReason reason
    );
}
