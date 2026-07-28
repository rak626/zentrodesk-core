package com.rakesh.zentrodesk.iam.principal;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public record PlatformPrincipal(
        UUID id,
        String email,
        String password,
        String firstName,
        String lastName,
        String roleName,
        boolean active,
        int sessionVersion,
        Collection<? extends GrantedAuthority> authorities
) implements CurrentUser {

    @Override
    public AccountType accountType() {
        return AccountType.PLATFORM;
    }

    @Override
    public Optional<UUID> tenantId() {
        return Optional.empty();
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
