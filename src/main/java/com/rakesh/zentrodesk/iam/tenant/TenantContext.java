package com.rakesh.zentrodesk.iam.tenant;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public Optional<UUID> currentTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public UUID requireTenantId() {
        return currentTenantId()
                .orElseThrow(() -> new UnauthorizedException("Tenant authentication required"));
    }

    public void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public void clear() {
        CURRENT_TENANT.remove();
    }

    public void require(UUID tenantId) {
        if (!requireTenantId().equals(tenantId)) {
            throw new UnauthorizedException("Access denied for tenant");
        }
    }
}
