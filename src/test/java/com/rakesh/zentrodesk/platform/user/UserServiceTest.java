package com.rakesh.zentrodesk.platform.user;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.platform.invitation.InvitationService;
import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TenantContext tenantContext = new TenantContext();
    private final RecordingRefreshTokenManager refreshTokenManager = new RecordingRefreshTokenManager();
    private final User user = User.builder()
            .id(userId)
            .tenant(Tenant.builder().id(tenantId).active(true).build())
            .role(Role.builder().id(UUID.randomUUID()).active(true).build())
            .firstName("Tenant")
            .email("tenant@example.com")
            .passwordHash("old-hash")
            .active(true)
            .sessionVersion(7)
            .build();
    private final UserRepository repository = (UserRepository) Proxy.newProxyInstance(
            UserRepository.class.getClassLoader(),
            new Class<?>[]{UserRepository.class},
            (unused, method, args) -> switch (method.getName()) {
                case "findWithTenantRoleAndPermissionsByIdAndTenantId" -> Optional.of(user);
                case "countByTenantIdAndRoleTemplateCodeAndActiveTrue" -> 1L;
                default -> throw new UnsupportedOperationException(method.getName());
            }
    );
    private final RecordingInvitationService invitationService = new RecordingInvitationService();
    private final UserService service = new UserService(
            repository,
            null,
            null,
            null,
            new CurrentTenant(tenantContext),
            refreshTokenManager,
            invitationService
    );

    @AfterEach
    void clearTenant() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void disableInvalidatesSessionsAndPendingInvitations() {
        tenantContext.setTenantId(tenantId);
        authenticate(UUID.randomUUID());

        service.delete(userId);

        assertEquals(false, user.isActive());
        assertEquals(8, user.getSessionVersion());
        assertEquals(userId, refreshTokenManager.revokedAccountId);
        assertEquals(userId, invitationService.revokedUserId);
    }

    @Test
    void cannotDisableOwnAccount() {
        tenantContext.setTenantId(tenantId);
        authenticate(userId);

        assertThrows(com.rakesh.zentrodesk.common.exception.BadRequestException.class,
                () -> service.disable(userId));
    }

    @Test
    void cannotDisableLastActiveOwner() {
        tenantContext.setTenantId(tenantId);
        authenticate(UUID.randomUUID());
        user.setRole(Role.builder()
                .id(UUID.randomUUID())
                .templateCode(FixedTenantRole.OWNER)
                .active(true)
                .build());

        assertThrows(com.rakesh.zentrodesk.common.exception.BadRequestException.class,
                () -> service.disable(userId));
    }

    @Test
    void revokeSessionsInvalidatesCurrentSessionVersion() {
        tenantContext.setTenantId(tenantId);
        authenticate(UUID.randomUUID());

        service.revokeSessions(userId);

        assertEquals(8, user.getSessionVersion());
        assertEquals(userId, refreshTokenManager.revokedAccountId);
    }

    private void authenticate(UUID currentUserId) {
        TenantPrincipal principal = new TenantPrincipal(
                currentUserId,
                tenantId,
                "owner@example.com",
                "",
                "Owner",
                null,
                UUID.randomUUID(),
                "Owner",
                true,
                0,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
    }

    private static final class RecordingRefreshTokenManager extends RefreshTokenManager {
        private UUID revokedAccountId;

        private RecordingRefreshTokenManager() {
            super(null, event -> { });
        }

        @Override
        public void revokeAccount(AccountType accountType, UUID accountId) {
            assertEquals(AccountType.TENANT, accountType);
            revokedAccountId = accountId;
        }
    }

    private static final class RecordingInvitationService extends InvitationService {
        private UUID revokedUserId;

        private RecordingInvitationService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void revokePendingForUser(UUID userId) {
            revokedUserId = userId;
        }
    }
}
