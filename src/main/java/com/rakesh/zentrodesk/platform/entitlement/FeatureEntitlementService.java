package com.rakesh.zentrodesk.platform.entitlement;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.web.CorrelationIds;
import com.rakesh.zentrodesk.platform.admin.PlatformAccess;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureEntitlementService {

    private final TenantEntitlementRepository entitlementRepository;
    private final TenantRepository tenantRepository;
    private final CurrentTenant currentTenant;
    private final AuditEventService auditEventService;

    @Transactional(readOnly = true)
    public List<EffectiveEntitlementResponse> findForCurrentTenant() {
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        boolean paidFeaturesAllowed = allowsPaidFeatures(tenant);
        return entitlementRepository.findAllByTenantIdOrderByFeatureKey(tenantId).stream()
                .map(entitlement -> new EffectiveEntitlementResponse(
                        entitlement.getFeatureKey(),
                        paidFeaturesAllowed && entitlement.isEnabled()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EntitlementResponse> findForPlatform(UUID tenantId) {
        return findForTenant(tenantId);
    }

    @Transactional
    public List<EntitlementResponse> updateForPlatform(UUID tenantId, EntitlementUpdateRequest request) {
        requireTenantManager();
        Tenant tenant = getTenant(tenantId);
        String beforeSummary = entitlementSummary(
                entitlementRepository.findAllByTenantIdOrderByFeatureKey(tenantId)
        );
        Set<String> featureKeys = new HashSet<>();
        for (EntitlementItemRequest item : request.entitlements()) {
            String featureKey = normalize(item.featureKey());
            if (!featureKeys.add(featureKey)) {
                throw new BadRequestException("Duplicate entitlement: " + featureKey);
            }
            TenantEntitlement entitlement = entitlementRepository
                    .findByTenantIdAndFeatureKey(tenantId, featureKey)
                    .orElseGet(() -> TenantEntitlement.builder()
                            .tenant(tenant)
                            .featureKey(featureKey)
                            .build());
            entitlement.setEnabled(item.enabled());
            entitlementRepository.save(entitlement);
        }
        List<TenantEntitlement> updatedEntitlements =
                entitlementRepository.findAllByTenantIdOrderByFeatureKey(tenantId);
        auditEventService.record(
                tenantId,
                PlatformAccess.currentUserId(),
                "TENANT_ENTITLEMENT",
                tenantId,
                "UPDATE",
                CorrelationIds.currentOrGenerated(),
                beforeSummary,
                entitlementSummary(updatedEntitlements)
        );
        boolean paidFeaturesAllowed = allowsPaidFeatures(tenant);
        return updatedEntitlements.stream()
                .map(entitlement -> toResponse(entitlement, paidFeaturesAllowed))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String featureKey) {
        return isEnabled(currentTenant.id(), featureKey);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String featureKey) {
        try {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant == null || !allowsPaidFeatures(tenant)) {
                return false;
            }
            return entitlementRepository.findByTenantIdAndFeatureKey(tenantId, normalize(featureKey))
                    .map(TenantEntitlement::isEnabled)
                    .orElse(false);
        } catch (DataAccessException exception) {
            log.warn("Entitlement lookup failed closed for tenant {}", tenantId);
            return false;
        }
    }

    private List<EntitlementResponse> findForTenant(UUID tenantId) {
        Tenant tenant = getTenant(tenantId);
        boolean paidFeaturesAllowed = allowsPaidFeatures(tenant);
        return entitlementRepository.findAllByTenantIdOrderByFeatureKey(tenantId).stream()
                .map(entitlement -> toResponse(entitlement, paidFeaturesAllowed))
                .toList();
    }

    private EntitlementResponse toResponse(TenantEntitlement entitlement, boolean paidFeaturesAllowed) {
        return new EntitlementResponse(
                entitlement.getFeatureKey(),
                entitlement.isEnabled(),
                paidFeaturesAllowed && entitlement.isEnabled(),
                entitlement.getCreatedAt(),
                entitlement.getUpdatedAt()
        );
    }

    private String entitlementSummary(List<TenantEntitlement> entitlements) {
        return entitlements.stream()
                .map(entitlement -> "\"" + entitlement.getFeatureKey() + "\":" + entitlement.isEnabled())
                .collect(Collectors.joining(",", "{\"entitlements\":{", "}}"));
    }

    private boolean allowsPaidFeatures(Tenant tenant) {
        return tenant.isActive() && tenant.getSubscriptionStatus().allowsPaidFeatures();
    }

    private Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private void requireTenantManager() {
        if (!PlatformAccess.canManageTenants(PlatformAccess.currentRole())) {
            throw new AccessDeniedException("Entitlement management requires platform admin access");
        }
    }

    private String normalize(String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            throw new BadRequestException("Feature key is required");
        }
        String normalized = featureKey.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z][A-Z0-9_]*$") || normalized.length() > 100) {
            throw new BadRequestException("Feature key is invalid");
        }
        return normalized;
    }
}
