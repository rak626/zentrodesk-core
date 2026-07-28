package com.rakesh.zentrodesk.platform.tenant;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public UUID userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof CurrentUser user && user.isTenantUser()) {
            return user.id();
        }
        throw new UnauthorizedException("Tenant authentication required");
    }
}
