package com.rakesh.zentrodesk.iam.refresh;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenManagerTest {

    private final FakeRepository fakeRepository = new FakeRepository();
    private final RefreshTokenManager manager = new RefreshTokenManager(fakeRepository.proxy, event -> { });

    @Test
    void storesOnlyHashAndRevokesFamilyWhenRotatedTokenIsReused() {
        UUID accountId = UUID.randomUUID();
        RefreshTokenManager.IssuedRefreshToken first = manager.issue(
                AccountType.TENANT,
                accountId,
                Instant.now().plusSeconds(300)
        );
        AccountRefreshToken firstEntity = fakeRepository.tokens.getFirst();

        assertNotEquals(first.token(), firstEntity.getTokenHash());
        assertEquals(RefreshTokenManager.hashToken(first.token()), firstEntity.getTokenHash());
        assertEquals(64, firstEntity.getTokenHash().length());

        RefreshTokenManager.ConsumedRefreshToken consumed = manager.consume(first.token(), AccountType.TENANT);
        assertTrue(consumed.valid());
        RefreshTokenManager.IssuedRefreshToken replacement = manager.issueReplacement(
                consumed,
                Instant.now().plusSeconds(300)
        );
        AccountRefreshToken replacementEntity = fakeRepository.tokens.getLast();
        assertEquals(firstEntity.getFamilyId(), replacementEntity.getFamilyId());
        assertEquals(firstEntity.getId(), replacementEntity.getParentTokenId());
        assertFalse(replacementEntity.isRevoked());

        RefreshTokenManager.ConsumedRefreshToken replay = manager.consume(first.token(), AccountType.TENANT);
        assertFalse(replay.valid());
        assertTrue(replacementEntity.isRevoked());
        assertNotNull(firstEntity.getReuseDetectedAt());
        assertNotEquals(replacement.token(), replacementEntity.getTokenHash());
    }

    @Test
    void doesNotReportAdministrativeRevocationAsReuse() {
        RefreshTokenManager.IssuedRefreshToken issued = manager.issue(
                AccountType.PLATFORM,
                UUID.randomUUID(),
                Instant.now().plusSeconds(300)
        );
        AccountRefreshToken entity = fakeRepository.tokens.getFirst();
        entity.setRevoked(true);
        entity.setRevokedReason(RefreshTokenRevocationReason.ADMIN_REVOKED);

        assertFalse(manager.consume(issued.token(), AccountType.PLATFORM).valid());
        assertNull(entity.getReuseDetectedAt());
    }

    private static final class FakeRepository {
        private final List<AccountRefreshToken> tokens = new ArrayList<>();

        private final AccountRefreshTokenRepository proxy = (AccountRefreshTokenRepository) Proxy.newProxyInstance(
                AccountRefreshTokenRepository.class.getClassLoader(),
                new Class<?>[]{AccountRefreshTokenRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        AccountRefreshToken token = (AccountRefreshToken) args[0];
                        if (token.getId() == null) {
                            token.setId(UUID.randomUUID());
                            tokens.add(token);
                        }
                        yield token;
                    }
                    case "findByTokenHashForUpdate" -> tokens.stream()
                            .filter(token -> token.getTokenHash().equals(args[0]))
                            .findFirst();
                    case "revokeActiveFamily" -> {
                        UUID familyId = (UUID) args[0];
                        Instant revokedAt = (Instant) args[1];
                        RefreshTokenRevocationReason reason = (RefreshTokenRevocationReason) args[2];
                        int count = 0;
                        for (AccountRefreshToken token : tokens) {
                            if (token.getFamilyId().equals(familyId) && !token.isRevoked()) {
                                token.setRevoked(true);
                                token.setRevokedAt(revokedAt);
                                token.setRevokedReason(reason);
                                count++;
                            }
                        }
                        yield count;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
