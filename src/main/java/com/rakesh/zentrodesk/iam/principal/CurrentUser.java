package com.rakesh.zentrodesk.iam.principal;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.UUID;

public interface CurrentUser extends UserDetails {

    UUID id();

    AccountType accountType();

    Optional<UUID> tenantId();

    String email();

    String roleName();

    default boolean isPlatformUser() {
        return accountType() == AccountType.PLATFORM;
    }

    default boolean isTenantUser() {
        return accountType() == AccountType.TENANT;
    }
}
