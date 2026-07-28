package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.user.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        UUID tenant,
        String email,
        String password,
        String firstName,
        String lastName,
        UUID roleId,
        String roleName,
        boolean active,
        int sessionVersion,
        Collection<? extends GrantedAuthority> authorities
) implements CurrentUser {

    public static AuthenticatedUser from(User user, Collection<? extends GrantedAuthority> authorities) {
        return new AuthenticatedUser(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getId(),
                user.getRole().getName(),
                user.isActive() && user.getRole().isActive() && user.getTenant().isActive(),
                user.getSessionVersion(),
                authorities
        );
    }

    @Override
    public AccountType accountType() {
        return AccountType.TENANT;
    }

    @Override
    public Optional<UUID> tenantId() {
        return Optional.of(tenant);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
