package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.authentication.AuthenticationSecurityEvent;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$10$p5fbPORpSB9ramt5/MkVt.hZzz963uDRioF4CdQMMtanz57GFtn06";

    private final UserRepository userRepository;
    private final RefreshTokenManager refreshTokenManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationThrottle authenticationThrottle;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationThrottle.requireAllowed(AccountType.TENANT, request.email());
        User user = resolveLoginUser(request);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash()
        );
        boolean active = user != null && user.isActive() && user.getRole().isActive() && user.getTenant().isActive();
        if (!passwordMatches || !active) {
            authenticationThrottle.recordFailure(AccountType.TENANT, request.email());
            publish(null, "LOGIN", "REJECTED");
            log.warn("Tenant login rejected");
            throw new UnauthorizedException("Invalid credentials");
        }
        authenticationThrottle.recordSuccess(AccountType.TENANT, request.email());
        publish(user.getId(), "LOGIN", "SUCCESS");

        user.setLastLoginAt(Instant.now());
        AuthenticatedUser principal = toPrincipal(user);
        JwtService.TokenPair accessToken = jwtService.generateAccessToken(principal);
        RefreshTokenManager.IssuedRefreshToken refreshToken = createRefreshToken(user.getId());
        return new LoginResponse(
                "Bearer",
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.token(),
                refreshToken.expiresAt(),
                toCurrentUser(principal)
        );
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        RefreshTokenManager.ConsumedRefreshToken consumed = refreshTokenManager.consume(request.refreshToken(), AccountType.TENANT);
        if (!consumed.valid()) {
            publish(null, "REFRESH", "REJECTED");
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        User user = userRepository.findWithTenantRoleAndPermissionsById(consumed.accountId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!user.isActive() || !user.getRole().isActive() || !user.getTenant().isActive()) {
            refreshTokenManager.revokeAccount(AccountType.TENANT, user.getId());
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }
        AuthenticatedUser principal = toPrincipal(user);
        JwtService.TokenPair accessToken = jwtService.generateAccessToken(principal);
        RefreshTokenManager.IssuedRefreshToken refreshToken = refreshTokenManager.issueReplacement(
                consumed,
                Instant.now().plusMillis(jwtProperties.refreshTokenExpiration())
        );
        publish(user.getId(), "REFRESH", "SUCCESS");
        return new RefreshTokenResponse(
                "Bearer",
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.token(),
                refreshToken.expiresAt()
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user) {
            return toCurrentUser(user);
        }
        throw new UnauthorizedException("Authentication required");
    }

    public AuthenticatedUser toPrincipal(User user) {
        return AuthenticatedUser.from(user, authorities(user));
    }

    private User resolveLoginUser(LoginRequest request) {
        if (StringUtils.hasText(request.tenantCode())) {
            return userRepository.findWithTenantRoleAndPermissionsByEmailAndTenantCode(request.email(), request.tenantCode())
                    .orElse(null);
        }
        List<User> matches = userRepository.findAllWithTenantRoleAndPermissionsByEmail(request.email());
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        if (!refreshTokenManager.revoke(request.refreshToken(), AccountType.TENANT)) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        publish(null, "LOGOUT", "SUCCESS");
    }

    private RefreshTokenManager.IssuedRefreshToken createRefreshToken(UUID userId) {
        return refreshTokenManager.issue(
                AccountType.TENANT,
                userId,
                Instant.now().plusMillis(jwtProperties.refreshTokenExpiration())
        );
    }

    private Set<GrantedAuthority> authorities(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeAuthority(user.getRole().getName())));
        user.getRole().getPermissions().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    private String normalizeAuthority(String value) {
        return value.trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase();
    }

    private void publish(UUID accountId, String action, String outcome) {
        eventPublisher.publishEvent(new AuthenticationSecurityEvent(
                Instant.now(),
                AccountType.TENANT,
                accountId,
                action,
                outcome
        ));
    }

    private CurrentUserResponse toCurrentUser(CurrentUser user) {
        Set<String> authorities = new LinkedHashSet<>();
        user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(authorities::add);
        return new CurrentUserResponse(
                user.id(),
                user.accountType(),
                user.tenantId().orElse(null),
                user.email(),
                user instanceof AuthenticatedUser tenantUser ? tenantUser.firstName() : null,
                user instanceof AuthenticatedUser tenantUser ? tenantUser.lastName() : null,
                user instanceof AuthenticatedUser tenantUser ? tenantUser.roleId() : null,
                user.roleName(),
                authorities
        );
    }
}
