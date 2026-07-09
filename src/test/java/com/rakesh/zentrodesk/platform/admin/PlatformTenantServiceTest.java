package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.role.RoleRepository;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlatformTenantServiceTest {

    private final FakeTenantRepository fakeTenantRepository = new FakeTenantRepository();
    private final FakeRoleRepository fakeRoleRepository = new FakeRoleRepository();
    private final FakeUserRepository fakeUserRepository = new FakeUserRepository();
    private final TenantRepository tenantRepository = fakeTenantRepository.proxy;
    private final RoleRepository roleRepository = fakeRoleRepository.proxy;
    private final UserRepository userRepository = fakeUserRepository.proxy;
    private final PasswordEncoder passwordEncoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded";
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
        }
    };
    private final PlatformTenantService service = new PlatformTenantService(tenantRepository, roleRepository, userRepository, passwordEncoder);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsTenantDefaultAdministratorRoleAndFirstAdminUser() {
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
                "Password123"
        );

        service.create(request);

        Tenant tenant = fakeTenantRepository.savedTenant;
        Role role = fakeRoleRepository.savedRole;
        User user = fakeUserRepository.savedUser;
        assertEquals("ACME", tenant.getCode());
        assertEquals("Administrator", role.getName());
        assertSame(tenant, role.getTenant());
        assertSame(tenant, user.getTenant());
        assertSame(role, user.getRole());
        assertEquals("encoded", user.getPasswordHash());
        assertEquals("admin@acme.test", user.getEmail());
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
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
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
        private Role savedRole;

        private final RoleRepository proxy = (RoleRepository) Proxy.newProxyInstance(
                RoleRepository.class.getClassLoader(),
                new Class<?>[]{RoleRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        savedRole = (Role) args[0];
                        savedRole.setId(UUID.randomUUID());
                        yield savedRole;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class FakeUserRepository {
        private User savedUser;

        private final UserRepository proxy = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        savedUser = (User) args[0];
                        savedUser.setId(UUID.randomUUID());
                        yield savedUser;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
