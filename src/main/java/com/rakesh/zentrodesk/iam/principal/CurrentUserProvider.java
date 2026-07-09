package com.rakesh.zentrodesk.iam.principal;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new UnauthorizedException("Authentication required");
    }

    public CurrentUser requirePlatform() {
        CurrentUser user = current();
        if (user.accountType() != AccountType.PLATFORM) {
            throw new UnauthorizedException("Platform account required");
        }
        return user;
    }

    public CurrentUser requireTenant() {
        CurrentUser user = current();
        if (user.accountType() != AccountType.TENANT) {
            throw new UnauthorizedException("Tenant account required");
        }
        return user;
    }
}
