package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PlatformAccess {

    private PlatformAccess() {
    }

    public static PlatformRole currentRole() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user && user.isPlatformUser()) {
            return PlatformRole.valueOf(user.roleName());
        }
        throw new UnauthorizedException("Platform authentication required");
    }

    public static boolean canManageTenants(PlatformRole role) {
        return role == PlatformRole.SUPER_ADMIN || role == PlatformRole.ADMIN;
    }
}
