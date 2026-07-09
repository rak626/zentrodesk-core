package com.rakesh.zentrodesk.platform.tenant;

import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CurrentTenant {

    private final TenantContext tenantContext;

    public UUID id() {
        return tenantContext.requireTenantId();
    }

    public void require(UUID tenantId) {
        tenantContext.require(tenantId);
    }
}
