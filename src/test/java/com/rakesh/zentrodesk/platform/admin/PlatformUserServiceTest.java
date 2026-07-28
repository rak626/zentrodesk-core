package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import com.rakesh.zentrodesk.platform.user.PlatformUser;
import com.rakesh.zentrodesk.platform.user.PlatformUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformUserServiceTest {

    private final FakePlatformUserRepository fakeRepository = new FakePlatformUserRepository();
    private final PlatformUserRepository repository = fakeRepository.proxy;
    private final RecordingRefreshTokenManager refreshTokenManager = new RecordingRefreshTokenManager();
    private final PasswordEncoder passwordEncoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded-" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
        }
    };
    private final PlatformUserService service = new PlatformUserService(
            repository,
            passwordEncoder,
            refreshTokenManager
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cannotDisableLastEnabledSuperAdmin() {
        UUID userId = UUID.randomUUID();
        authenticateSuperAdmin(userId);
        PlatformUser user = PlatformUser.builder()
                .id(userId)
                .firstName("Platform")
                .email("platform@example.com")
                .passwordHash("hash")
                .role(PlatformRole.SUPER_ADMIN)
                .enabled(true)
                .build();
        fakeRepository.user = user;
        fakeRepository.enabledSuperAdmins = 1L;

        assertThrows(BadRequestException.class, () -> service.disable(userId));

        assertTrue(fakeRepository.countCalled);
    }

    @Test
    void cannotDemoteLastEnabledSuperAdmin() {
        UUID userId = UUID.randomUUID();
        authenticateSuperAdmin(userId);
        PlatformUser user = PlatformUser.builder()
                .id(userId)
                .firstName("Platform")
                .email("platform@example.com")
                .passwordHash("hash")
                .role(PlatformRole.SUPER_ADMIN)
                .enabled(true)
                .build();
        fakeRepository.user = user;
        fakeRepository.enabledSuperAdmins = 1L;

        PlatformUserUpdateRequest request = new PlatformUserUpdateRequest(
                "Platform",
                "Admin",
                "platform@example.com",
                PlatformRole.ADMIN,
                true
        );

        assertThrows(BadRequestException.class, () -> service.update(userId, request));
    }

    @Test
    void passwordResetInvalidatesExistingSessions() {
        UUID userId = UUID.randomUUID();
        authenticateSuperAdmin(UUID.randomUUID());
        PlatformUser user = PlatformUser.builder()
                .id(userId)
                .firstName("Platform")
                .email("admin@example.com")
                .passwordHash("old-hash")
                .role(PlatformRole.ADMIN)
                .enabled(true)
                .sessionVersion(3)
                .build();
        fakeRepository.user = user;

        service.resetPassword(userId, new com.rakesh.zentrodesk.platform.user.PasswordResetRequest("SecurePass1!"));

        assertEquals("encoded-SecurePass1!", user.getPasswordHash());
        assertEquals(4, user.getSessionVersion());
        assertEquals(userId, refreshTokenManager.revokedAccountId);
    }

    private void authenticateSuperAdmin(UUID userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                "platform@example.com",
                "hash",
                "Platform",
                "Admin",
                "SUPER_ADMIN",
                true,
                0,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private static final class FakePlatformUserRepository {
        private PlatformUser user;
        private long enabledSuperAdmins;
        private boolean countCalled;

        private final PlatformUserRepository proxy = (PlatformUserRepository) Proxy.newProxyInstance(
                PlatformUserRepository.class.getClassLoader(),
                new Class<?>[]{PlatformUserRepository.class},
                (unused, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(user);
                    case "existsByEmailExcludingUser" -> false;
                    case "countByRoleAndEnabledTrue" -> {
                        countCalled = true;
                        yield enabledSuperAdmins;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

    }

    private static final class RecordingRefreshTokenManager extends RefreshTokenManager {
        private UUID revokedAccountId;

        private RecordingRefreshTokenManager() {
            super(null, event -> { });
        }

        @Override
        public void revokeAccount(AccountType accountType, UUID accountId) {
            assertEquals(AccountType.PLATFORM, accountType);
            revokedAccountId = accountId;
        }
    }
}
