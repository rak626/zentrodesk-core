package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.iam.refresh.AccountRefreshToken;
import com.rakesh.zentrodesk.iam.refresh.AccountRefreshTokenRepository;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = resolveLoginUser(request);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Invalid login attempt for email {}", request.email());
            throw new UnauthorizedException("Invalid credentials");
        }
        if (!user.isActive() || !user.getRole().isActive() || !user.getTenant().isActive()) {
            throw new UnauthorizedException("User account is not active");
        }

        user.setLastLoginAt(Instant.now());
        AuthenticatedUser principal = toPrincipal(user);
        JwtService.TokenPair accessToken = jwtService.generateAccessToken(principal);
        AccountRefreshToken refreshToken = createRefreshToken(user.getId());
        return new LoginResponse(
                "Bearer",
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.getToken(),
                refreshToken.getExpiresAt(),
                toCurrentUser(principal)
        );
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        AccountRefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (existing.getAccountType() != AccountType.TENANT || existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        User user = userRepository.findWithTenantRoleAndPermissionsById(existing.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        AuthenticatedUser principal = toPrincipal(user);
        JwtService.TokenPair accessToken = jwtService.generateAccessToken(principal);
        AccountRefreshToken refreshToken = createRefreshToken(user.getId());
        return new RefreshTokenResponse(
                "Bearer",
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.getToken(),
                refreshToken.getExpiresAt()
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
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }
        List<User> matches = userRepository.findAllWithTenantRoleAndPermissionsByEmail(request.email());
        if (matches.isEmpty()) {
            throw new UnauthorizedException("Invalid credentials");
        }
        if (matches.size() > 1) {
            throw new BadRequestException("Tenant code is required for this email");
        }
        return matches.getFirst();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        AccountRefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (existing.getAccountType() != AccountType.TENANT) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
    }

    private AccountRefreshToken createRefreshToken(UUID userId) {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        AccountRefreshToken refreshToken = AccountRefreshToken.builder()
                .accountType(AccountType.TENANT)
                .accountId(userId)
                .token(Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes))
                .expiresAt(Instant.now().plusMillis(jwtProperties.refreshTokenExpiration()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
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
