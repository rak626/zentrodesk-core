package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.platform.admin.PlatformTenantService;
import com.rakesh.zentrodesk.platform.admin.PlatformTenantUpdateRequest;
import com.rakesh.zentrodesk.platform.entitlement.EntitlementItemRequest;
import com.rakesh.zentrodesk.platform.entitlement.EntitlementUpdateRequest;
import com.rakesh.zentrodesk.platform.entitlement.FeatureEntitlementService;
import com.rakesh.zentrodesk.platform.tenant.SubscriptionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvisioningAndEntitlementsIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private PlatformTenantService tenantService;

    @Autowired
    private FeatureEntitlementService entitlementService;

    @AfterEach
    void clearContexts() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void managesProvisioningEntitlementsSuspensionAndTenantBoundaries() {
        UUID tenantA = insertTenant("PLAN16A");
        UUID tenantB = insertTenant("PLAN16B");
        authenticatePlatformAdmin();

        entitlementService.updateForPlatform(tenantA, new EntitlementUpdateRequest(List.of(
                new EntitlementItemRequest("ADVANCED_REPORTS", true),
                new EntitlementItemRequest("AUTOMATIONS", false)
        )));

        assertEquals(2, entitlementService.findForPlatform(tenantA).size());
        assertTrue(entitlementService.isEnabled(tenantA, "ADVANCED_REPORTS"));
        assertFalse(entitlementService.isEnabled(tenantB, "ADVANCED_REPORTS"));
        assertThrows(BadRequestException.class, () -> entitlementService.updateForPlatform(
                tenantA,
                new EntitlementUpdateRequest(List.of(
                        new EntitlementItemRequest("AUTOMATIONS", true),
                        new EntitlementItemRequest("automations", false)
                ))
        ));

        var updated = tenantService.update(tenantA, new PlatformTenantUpdateRequest(
                "Plan 16 Tenant",
                "PLAN16A",
                null,
                null,
                null,
                null,
                null,
                null,
                "Asia/Kolkata",
                "INR",
                true,
                SubscriptionStatus.PAST_DUE,
                "PRO",
                12
        ));
        assertEquals(SubscriptionStatus.PAST_DUE, updated.subscriptionStatus());
        assertEquals("PRO", updated.planCode());
        assertEquals(12, updated.seatLimit());
        assertFalse(entitlementService.isEnabled(tenantA, "ADVANCED_REPORTS"));

        tenantService.disable(tenantA);
        assertFalse(tenantActive(tenantA));
        assertFalse(entitlementService.isEnabled(tenantA, "ADVANCED_REPORTS"));

        tenantContext.setTenantId(tenantB);
        assertTrue(entitlementService.findForCurrentTenant().isEmpty());

        Integer auditCount = jdbcClient.sql("""
                        select count(*) from zentro_desk.audit_events
                        where tenant_id = :tenantId
                          and action in ('UPDATE', 'PROVISIONING_UPDATE', 'SUSPEND')
                        """)
                .param("tenantId", tenantA)
                .query(Integer.class)
                .single();
        assertEquals(3, auditCount);
    }

    private UUID insertTenant(String codePrefix) {
        UUID tenantId = UUID.randomUUID();
        String code = codePrefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, 'Plan 16 Tenant', :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("code", code)
                .update();
        return tenantId;
    }

    private boolean tenantActive(UUID tenantId) {
        return jdbcClient.sql("select active from zentro_desk.tenants where id = :id")
                .param("id", tenantId)
                .query(Boolean.class)
                .single();
    }

    private void authenticatePlatformAdmin() {
        PlatformPrincipal principal = new PlatformPrincipal(
                UUID.randomUUID(),
                "platform-admin@example.test",
                "hash",
                "Platform",
                "Admin",
                "ADMIN",
                true,
                0,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
