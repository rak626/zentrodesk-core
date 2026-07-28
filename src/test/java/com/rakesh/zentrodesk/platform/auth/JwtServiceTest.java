package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(
            "test-secret-with-enough-entropy-for-hmac-signing",
            null,
            900_000,
            604_800_000
    ));

    @Test
    void generatesPlatformTokenWithoutTenantClaim() {
        UUID userId = UUID.randomUUID();
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                "platform@example.com",
                "password",
                "Platform",
                "Admin",
                "SUPER_ADMIN",
                true,
                0,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        JwtService.ParsedToken parsedToken = jwtService.parseToken(jwtService.generateAccessToken(principal).token());

        assertEquals(userId, parsedToken.subject());
        assertEquals(AccountType.PLATFORM, parsedToken.accountType());
        assertFalse(parsedToken.tenantId().isPresent());
    }

    @Test
    void generatesTenantTokenWithTenantClaim() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantPrincipal principal = new TenantPrincipal(
                userId,
                tenantId,
                "tenant@example.com",
                "password",
                "Tenant",
                "Owner",
                UUID.randomUUID(),
                "OWNER",
                true,
                0,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        );

        JwtService.ParsedToken parsedToken = jwtService.parseToken(jwtService.generateAccessToken(principal).token());

        assertEquals(userId, parsedToken.subject());
        assertEquals(AccountType.TENANT, parsedToken.accountType());
        assertEquals(tenantId, parsedToken.tenantId().orElseThrow());
    }

    @Test
    void rejectsTokenAfterSessionVersionChanges() {
        UUID userId = UUID.randomUUID();
        PlatformPrincipal original = platformPrincipal(userId, 0);
        String token = jwtService.generateAccessToken(original).token();

        assertTrue(jwtService.isValid(token, original));
        assertFalse(jwtService.isValid(token, platformPrincipal(userId, 1)));
    }

    @Test
    void acceptsPreviousSecretDuringRotationAndSignsWithCurrentSecret() {
        String oldSecret = "old-test-secret-with-enough-entropy-for-signing";
        String newSecret = "new-test-secret-with-enough-entropy-for-signing";
        JwtService oldService = new JwtService(new JwtProperties(oldSecret, null, 900_000, 604_800_000));
        JwtService rotatingService = new JwtService(new JwtProperties(newSecret, oldSecret, 900_000, 604_800_000));
        PlatformPrincipal principal = platformPrincipal(UUID.randomUUID(), 0);

        assertTrue(rotatingService.isValid(oldService.generateAccessToken(principal).token(), principal));

        String newlySignedToken = rotatingService.generateAccessToken(principal).token();
        assertTrue(rotatingService.isValid(newlySignedToken, principal));
        org.junit.jupiter.api.Assertions.assertThrows(
                io.jsonwebtoken.JwtException.class,
                () -> oldService.parseToken(newlySignedToken)
        );
    }

    private PlatformPrincipal platformPrincipal(UUID userId, int sessionVersion) {
        return new PlatformPrincipal(
                userId,
                "platform@example.com",
                "password",
                "Platform",
                "Admin",
                "SUPER_ADMIN",
                true,
                sessionVersion,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }
}
