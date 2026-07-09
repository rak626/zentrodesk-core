package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.refresh.AccountRefreshToken;
import com.rakesh.zentrodesk.iam.refresh.AccountRefreshTokenRepository;
import com.rakesh.zentrodesk.platform.user.PlatformUser;
import com.rakesh.zentrodesk.platform.user.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformUserRepository platformUserRepository;
    private final AccountRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResponse login(PlatformLoginRequest request) {
        PlatformUser user = platformUserRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Invalid platform login attempt for email {}", request.email());
            throw new UnauthorizedException("Invalid credentials");
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("User account is not active");
        }

        user.setLastLoginAt(Instant.now());
        PlatformPrincipal principal = toPrincipal(user);
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
        if (existing.getAccountType() != AccountType.PLATFORM || existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        PlatformUser user = platformUserRepository.findById(existing.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Platform user not found"));
        PlatformPrincipal principal = toPrincipal(user);
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

    @Transactional
    public void logout(RefreshTokenRequest request) {
        AccountRefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (existing.getAccountType() != AccountType.PLATFORM) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user && user.isPlatformUser()) {
            return toCurrentUser(user);
        }
        throw new UnauthorizedException("Platform authentication required");
    }

    public PlatformPrincipal toPrincipal(PlatformUser user) {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new PlatformPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isEnabled(),
                authorities
        );
    }

    private AccountRefreshToken createRefreshToken(UUID userId) {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        AccountRefreshToken refreshToken = AccountRefreshToken.builder()
                .accountType(AccountType.PLATFORM)
                .accountId(userId)
                .token(Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes))
                .expiresAt(Instant.now().plusMillis(jwtProperties.refreshTokenExpiration()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private CurrentUserResponse toCurrentUser(CurrentUser user) {
        Set<String> authorities = new LinkedHashSet<>();
        user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(authorities::add);
        String firstName = user instanceof PlatformPrincipal platformUser ? platformUser.firstName() : null;
        String lastName = user instanceof PlatformPrincipal platformUser ? platformUser.lastName() : null;
        return new CurrentUserResponse(
                user.id(),
                user.accountType(),
                null,
                user.email(),
                firstName,
                lastName,
                null,
                user.roleName(),
                authorities
        );
    }
}
