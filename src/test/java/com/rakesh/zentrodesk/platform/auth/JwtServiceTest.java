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

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(
            "test-secret-with-enough-entropy-for-hmac-signing",
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
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        );

        JwtService.ParsedToken parsedToken = jwtService.parseToken(jwtService.generateAccessToken(principal).token());

        assertEquals(userId, parsedToken.subject());
        assertEquals(AccountType.TENANT, parsedToken.accountType());
        assertEquals(tenantId, parsedToken.tenantId().orElseThrow());
    }
}
