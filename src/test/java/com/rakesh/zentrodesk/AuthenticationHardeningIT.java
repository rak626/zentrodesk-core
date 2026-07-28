package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationHardeningIT extends PostgreSqlIntegrationTest {

    @Autowired
    private RefreshTokenManager refreshTokenManager;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void hashesRefreshTokensAndPersistsFamilyRevocationOnReuse() {
        RefreshTokenManager.IssuedRefreshToken first = refreshTokenManager.issue(
                AccountType.TENANT,
                UUID.randomUUID(),
                Instant.now().plusSeconds(300)
        );
        String storedHash = jdbcClient.sql("""
                        select token_hash
                        from zentro_desk.account_refresh_tokens
                        where token_hash <> :rawToken
                        """)
                .param("rawToken", first.token())
                .query(String.class)
                .single();
        assertNotEquals(first.token(), storedHash);
        assertTrue(storedHash.matches("[0-9a-f]{64}"));

        RefreshTokenManager.ConsumedRefreshToken consumed = refreshTokenManager.consume(first.token(), AccountType.TENANT);
        assertTrue(consumed.valid());
        refreshTokenManager.issueReplacement(consumed, Instant.now().plusSeconds(300));

        RefreshTokenManager.ConsumedRefreshToken replay = refreshTokenManager.consume(first.token(), AccountType.TENANT);
        assertFalse(replay.valid());

        Boolean replacementRevoked = jdbcClient.sql("""
                        select revoked
                        from zentro_desk.account_refresh_tokens
                        where parent_token_id = :parentTokenId
                        """)
                .param("parentTokenId", consumed.tokenId())
                .query(Boolean.class)
                .single();
        Instant reuseDetectedAt = jdbcClient.sql("""
                        select reuse_detected_at
                        from zentro_desk.account_refresh_tokens
                        where id = :tokenId
                        """)
                .param("tokenId", consumed.tokenId())
                .query(Instant.class)
                .single();
        Integer rawTokenRows = jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.account_refresh_tokens
                        where token_hash = :rawToken
                        """)
                .param("rawToken", first.token())
                .query(Integer.class)
                .single();

        assertEquals(Boolean.TRUE, replacementRevoked);
        assertNotNull(reuseDetectedAt);
        assertEquals(0, rawTokenRows);
    }
}
