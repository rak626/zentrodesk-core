package com.rakesh.zentrodesk.iam.refresh;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.authentication.AuthenticationSecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRefreshTokenRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public IssuedRefreshToken issue(AccountType accountType, UUID accountId, Instant expiresAt) {
        return issue(accountType, accountId, expiresAt, UUID.randomUUID(), null);
    }

    @Transactional
    public IssuedRefreshToken issueReplacement(ConsumedRefreshToken consumed, Instant expiresAt) {
        if (!consumed.valid()) {
            throw new IllegalArgumentException("Cannot replace an invalid refresh token");
        }
        return issue(consumed.accountType(), consumed.accountId(), expiresAt, consumed.familyId(), consumed.tokenId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsumedRefreshToken consume(String rawToken, AccountType expectedType) {
        AccountRefreshToken token = repository.findByTokenHashForUpdate(hashToken(rawToken)).orElse(null);
        if (token == null || token.getAccountType() != expectedType) {
            return ConsumedRefreshToken.invalid();
        }

        Instant now = Instant.now();
        if (token.isRevoked()) {
            if (token.getRevokedReason() == RefreshTokenRevocationReason.ROTATED) {
                token.setReuseDetectedAt(now);
                repository.revokeActiveFamily(token.getFamilyId(), now, RefreshTokenRevocationReason.REUSE);
                repository.save(token);
                publish(token.getAccountType(), token.getAccountId(), "REFRESH_TOKEN_REUSE", "REJECTED");
            }
            return ConsumedRefreshToken.invalid();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            token.setRevoked(true);
            token.setRevokedAt(now);
            token.setRevokedReason(RefreshTokenRevocationReason.EXPIRED);
            return ConsumedRefreshToken.invalid();
        }

        token.setRevoked(true);
        token.setRevokedAt(now);
        token.setRevokedReason(RefreshTokenRevocationReason.ROTATED);
        return new ConsumedRefreshToken(
                true,
                token.getAccountType(),
                token.getAccountId(),
                token.getFamilyId(),
                token.getId()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean revoke(String rawToken, AccountType expectedType) {
        AccountRefreshToken token = repository.findByTokenHashForUpdate(hashToken(rawToken)).orElse(null);
        if (token == null || token.getAccountType() != expectedType) {
            return false;
        }
        if (!token.isRevoked()) {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            token.setRevokedReason(RefreshTokenRevocationReason.LOGOUT);
        }
        return true;
    }

    @Transactional
    public void revokeAccount(AccountType accountType, UUID accountId) {
        repository.revokeActiveAccount(
                accountType,
                accountId,
                Instant.now(),
                RefreshTokenRevocationReason.ADMIN_REVOKED
        );
        publish(accountType, accountId, "SESSIONS_REVOKED", "SUCCESS");
    }

    @Transactional
    public void revokeTenant(UUID tenantId) {
        repository.revokeActiveTenantAccounts(
                AccountType.TENANT,
                tenantId,
                Instant.now(),
                RefreshTokenRevocationReason.ADMIN_REVOKED
        );
        publish(AccountType.TENANT, null, "TENANT_SESSIONS_REVOKED", "SUCCESS");
    }

    private IssuedRefreshToken issue(
            AccountType accountType,
            UUID accountId,
            Instant expiresAt,
            UUID familyId,
            UUID parentTokenId
    ) {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        AccountRefreshToken token = repository.save(AccountRefreshToken.builder()
                .accountType(accountType)
                .accountId(accountId)
                .tokenHash(hashToken(rawToken))
                .familyId(familyId)
                .parentTokenId(parentTokenId)
                .expiresAt(expiresAt)
                .revoked(false)
                .build());
        return new IssuedRefreshToken(rawToken, token.getExpiresAt());
    }

    static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void publish(AccountType accountType, UUID accountId, String action, String outcome) {
        eventPublisher.publishEvent(new AuthenticationSecurityEvent(
                Instant.now(),
                accountType,
                accountId,
                action,
                outcome
        ));
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    public record ConsumedRefreshToken(
            boolean valid,
            AccountType accountType,
            UUID accountId,
            UUID familyId,
            UUID tokenId
    ) {
        static ConsumedRefreshToken invalid() {
            return new ConsumedRefreshToken(false, null, null, null, null);
        }
    }
}
