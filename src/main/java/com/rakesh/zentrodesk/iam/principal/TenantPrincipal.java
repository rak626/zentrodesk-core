package com.rakesh.zentrodesk.iam.principal;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public record TenantPrincipal(
        UUID id,
        UUID tenant,
        String email,
        String password,
        String firstName,
        String lastName,
        UUID roleId,
        String roleName,
        boolean active,
        Collection<? extends GrantedAuthority> authorities
) implements CurrentUser {

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
