package com.rakesh.zentrodesk.platform.tenant;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentTenantTest {

    private final TenantContext tenantContext = new TenantContext();
    private final CurrentTenant currentTenant = new CurrentTenant(tenantContext);

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void returnsTenantIdFromAuthenticatedPrincipal() {
        UUID tenantId = UUID.randomUUID();
        tenantContext.setTenantId(tenantId);

        assertEquals(tenantId, currentTenant.id());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(UnauthorizedException.class, currentTenant::id);
    }

    @Test
    void rejectsDifferentTenant() {
        tenantContext.setTenantId(UUID.randomUUID());

        assertThrows(UnauthorizedException.class, () -> currentTenant.require(UUID.randomUUID()));
    }
}
