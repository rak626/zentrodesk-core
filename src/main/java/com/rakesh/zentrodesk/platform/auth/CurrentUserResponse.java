package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.iam.authentication.AccountType;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        AccountType accountType,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        UUID roleId,
        String roleName,
        Set<String> authorities
) {
}
