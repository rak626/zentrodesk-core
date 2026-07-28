package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.exception.UnauthorizedException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.authentication.AuthenticationSecurityEvent;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.user.PlatformUser;
import com.rakesh.zentrodesk.platform.user.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuthenticationService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$10$p5fbPORpSB9ramt5/MkVt.hZzz963uDRioF4CdQMMtanz57GFtn06";

    private final PlatformUserRepository platformUserRepository;
    private final RefreshTokenManager refreshTokenManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationThrottle authenticationThrottle;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LoginResponse login(PlatformLoginRequest request) {
        authenticationThrottle.requireAllowed(AccountType.PLATFORM, request.email());
        PlatformUser user = platformUserRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash()
        );
        if (!passwordMatches || user == null || !user.isEnabled()) {
            authenticationThrottle.recordFailure(AccountType.PLATFORM, request.email());
            publish(null, "LOGIN", "REJECTED");
            log.warn("Platform login rejected");
            throw new UnauthorizedException("Invalid credentials");
        }
        authenticationThrottle.recordSuccess(AccountType.PLATFORM, request.email());
        publish(user.getId(), "LOGIN", "SUCCESS");

        user.setLastLoginAt(Instant.now());
        PlatformPrincipal principal = toPrincipal(user);
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
        RefreshTokenManager.ConsumedRefreshToken consumed = refreshTokenManager.consume(request.refreshToken(), AccountType.PLATFORM);
        if (!consumed.valid()) {
            publish(null, "REFRESH", "REJECTED");
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        PlatformUser user = platformUserRepository.findById(consumed.accountId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!user.isEnabled()) {
            refreshTokenManager.revokeAccount(AccountType.PLATFORM, user.getId());
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }
        PlatformPrincipal principal = toPrincipal(user);
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

    @Transactional
    public void logout(RefreshTokenRequest request) {
        if (!refreshTokenManager.revoke(request.refreshToken(), AccountType.PLATFORM)) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        publish(null, "LOGOUT", "SUCCESS");
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
                user.getSessionVersion(),
                authorities
        );
    }

    private RefreshTokenManager.IssuedRefreshToken createRefreshToken(UUID userId) {
        return refreshTokenManager.issue(
                AccountType.PLATFORM,
                userId,
                Instant.now().plusMillis(jwtProperties.refreshTokenExpiration())
        );
    }

    private void publish(UUID accountId, String action, String outcome) {
        eventPublisher.publishEvent(new AuthenticationSecurityEvent(
                Instant.now(),
                AccountType.PLATFORM,
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
