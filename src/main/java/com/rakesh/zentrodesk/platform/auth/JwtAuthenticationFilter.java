package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.iam.principal.PlatformPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.platform.user.PlatformUser;
import com.rakesh.zentrodesk.platform.user.PlatformUserRepository;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;
    private final AuthenticationService authenticationService;
    private final PlatformAuthenticationService platformAuthenticationService;
    private final TenantContext tenantContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authorization.substring(BEARER_PREFIX.length());
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                JwtService.ParsedToken parsedToken = jwtService.parseToken(token);
                if (!tokenTypeMatchesRequest(parsedToken.accountType(), request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                CurrentUser principal = loadPrincipal(parsedToken);
                if (principal != null && jwtService.isValid(token, principal)) {
                    principal.tenantId().ifPresent(tenantContext::setTenantId);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Ignoring invalid JWT", ex);
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

    private CurrentUser loadPrincipal(JwtService.ParsedToken parsedToken) {
        if (parsedToken.accountType() == AccountType.PLATFORM) {
            PlatformUser user = platformUserRepository.findById(parsedToken.subject()).orElse(null);
            return user == null ? null : platformAuthenticationService.toPrincipal(user);
        }
        User user = userRepository.findWithTenantRoleAndPermissionsById(parsedToken.subject()).orElse(null);
        return user == null ? null : authenticationService.toPrincipal(user);
    }

    private boolean tokenTypeMatchesRequest(AccountType accountType, HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/platform/")) {
            return accountType == AccountType.PLATFORM;
        }
        if (path.startsWith("/api/app/")) {
            return accountType == AccountType.TENANT;
        }
        return true;
    }
}
