package com.rakesh.zentrodesk.platform.entitlement;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.SubscriptionStatus;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureEntitlementServiceTest {

    private final TenantEntitlementRepository entitlementRepository = mock(TenantEntitlementRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final FeatureEntitlementService service = new FeatureEntitlementService(
            entitlementRepository,
            tenantRepository,
            mock(CurrentTenant.class),
            mock(AuditEventService.class)
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paidFeaturesAreEffectiveOnlyForEligibleSubscriptions() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .active(true)
                .subscriptionStatus(SubscriptionStatus.PAST_DUE)
                .build();
        TenantEntitlement entitlement = TenantEntitlement.builder()
                .tenant(tenant)
                .featureKey("ADVANCED_REPORTS")
                .enabled(true)
                .build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(entitlementRepository.findAllByTenantIdOrderByFeatureKey(tenantId))
                .thenReturn(List.of(entitlement));
        when(entitlementRepository.findByTenantIdAndFeatureKey(tenantId, "ADVANCED_REPORTS"))
                .thenReturn(Optional.of(entitlement));

        assertFalse(service.findForPlatform(tenantId).getFirst().effective());
        assertFalse(service.isEnabled(tenantId, "advanced_reports"));

        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);

        assertTrue(service.findForPlatform(tenantId).getFirst().effective());
        assertTrue(service.isEnabled(tenantId, "advanced_reports"));
    }

    @Test
    void lookupFailuresDefaultClosed() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertFalse(service.isEnabled(tenantId, "ADVANCED_REPORTS"));
    }

    @Test
    void rejectsDuplicateFeatureKeysIgnoringCase() {
        UUID tenantId = UUID.randomUUID();
        authenticatePlatformAdmin();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                Tenant.builder().id(tenantId).active(true).build()
        ));

        assertThrows(BadRequestException.class, () -> service.updateForPlatform(
                tenantId,
                new EntitlementUpdateRequest(List.of(
                        new EntitlementItemRequest("advanced_reports", true),
                        new EntitlementItemRequest("ADVANCED_REPORTS", false)
                ))
        ));
    }

    private void authenticatePlatformAdmin() {
        PlatformPrincipal principal = new PlatformPrincipal(
                UUID.randomUUID(),
                "platform@example.test",
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
