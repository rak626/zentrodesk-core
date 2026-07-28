package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.invitation.InvitationService;
import com.rakesh.zentrodesk.platform.invitation.InvitationResponse;
import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.role.RoleRepository;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.tenant.SubscriptionStatus;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlatformTenantServiceTest {

    private final FakeTenantRepository fakeTenantRepository = new FakeTenantRepository();
    private final FakeRoleRepository fakeRoleRepository = new FakeRoleRepository();
    private final TenantRepository tenantRepository = fakeTenantRepository.proxy;
    private final RoleRepository roleRepository = fakeRoleRepository.proxy;
    private final UserRepository userRepository = userRepository();
    private final RecordingInvitationService invitationService = new RecordingInvitationService();
    private final PlatformTenantService service = new PlatformTenantService(
            tenantRepository,
            roleRepository,
            new RefreshTokenManager(null, event -> { }),
            invitationService,
            userRepository,
            new RecordingAuditEventService()
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsFixedRolesAndAssignsFirstUserAsOwner() {
        authenticateAdmin("SUPER_ADMIN");

        PlatformTenantCreateRequest request = new PlatformTenantCreateRequest(
                "Acme Travel",
                "ACME",
                "hello@acme.test",
                "+1000000000",
                "Main Street",
                "Gangtok",
                "Sikkim",
                "India",
                "Asia/Kolkata",
                "INR",
                "Tenant",
                "Admin",
                "admin@acme.test",
                "+1000000001",
                SubscriptionStatus.ACTIVE,
                "PRO",
                10
        );

        service.create(request);

        Tenant tenant = fakeTenantRepository.savedTenant;
        Role role = fakeRoleRepository.savedRoles.stream()
                .filter(savedRole -> savedRole.getTemplateCode() == FixedTenantRole.OWNER)
                .findFirst()
                .orElseThrow();
        assertEquals("ACME", tenant.getCode());
        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        assertEquals("PRO", tenant.getPlanCode());
        assertEquals(10, tenant.getSeatLimit());
        assertEquals(5, fakeRoleRepository.savedRoles.size());
        assertEquals("Owner", role.getName());
        assertEquals(FixedTenantRole.OWNER, role.getTemplateCode());
        assertEquals(FixedTenantRole.values().length, fakeRoleRepository.savedRoles.stream()
                .map(Role::getTemplateCode)
                .distinct()
                .count());
        assertSame(tenant, role.getTenant());
        assertSame(tenant, invitationService.tenant);
        assertSame(role, invitationService.role);
        assertEquals("Tenant", invitationService.firstName);
        assertEquals("Admin", invitationService.lastName);
        assertEquals("admin@acme.test", invitationService.email);
        assertEquals("+1000000001", invitationService.phone);
    }

    private void authenticateAdmin(String roleName) {
        PlatformPrincipal principal = new PlatformPrincipal(
                UUID.randomUUID(),
                "platform@example.com",
                "hash",
                "Platform",
                "Admin",
                roleName,
                true,
                0,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "countOccupiedSeats" -> 0L;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class FakeTenantRepository {
        private Tenant savedTenant;

        private final TenantRepository proxy = (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "existsByCodeExcludingTenant" -> false;
                    case "save" -> {
                        savedTenant = (Tenant) args[0];
                        savedTenant.setId(UUID.randomUUID());
                        yield savedTenant;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class FakeRoleRepository {
        private final List<Role> savedRoles = new java.util.ArrayList<>();

        private final RoleRepository proxy = (RoleRepository) Proxy.newProxyInstance(
                RoleRepository.class.getClassLoader(),
                new Class<?>[]{RoleRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        Role savedRole = (Role) args[0];
                        savedRole.setId(UUID.randomUUID());
                        savedRoles.add(savedRole);
                        yield savedRole;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class RecordingInvitationService extends InvitationService {
        private Tenant tenant;
        private Role role;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;

        private RecordingInvitationService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public InvitationResponse inviteOwner(
                Tenant tenant,
                Role ownerRole,
                String firstName,
                String lastName,
                String email,
                String phone,
                UUID invitedBy
        ) {
            this.tenant = tenant;
            this.role = ownerRole;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phone = phone;
            return null;
        }
    }

    private static final class RecordingAuditEventService extends AuditEventService {

        private RecordingAuditEventService() {
            super(null, null);
        }

        @Override
        public void record(
                UUID tenantId,
                UUID actorId,
                String entityType,
                UUID entityId,
                String action,
                String correlationId,
                String beforeSummary,
                String afterSummary
        ) {
        }
    }

}
