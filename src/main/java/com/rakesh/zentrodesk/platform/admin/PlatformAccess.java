package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class PlatformAccess {

    private PlatformAccess() {
    }

    public static PlatformRole currentRole() {
        return PlatformRole.valueOf(currentUser().roleName());
    }

    public static UUID currentUserId() {
        return currentUser().id();
    }

    public static boolean canManageTenants(PlatformRole role) {
        return role == PlatformRole.SUPER_ADMIN || role == PlatformRole.ADMIN;
    }

    private static CurrentUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user && user.isPlatformUser()) {
            return user;
        }
        throw new UnauthorizedException("Platform authentication required");
    }
}
