package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Optional<SecretKey> previousSigningKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = createSigningKey(properties.secret());
        this.previousSigningKey = Optional.ofNullable(properties.previousSecret())
                .filter(secret -> !secret.isBlank())
                .map(this::createSigningKey);
    }

    public TokenPair generateAccessToken(CurrentUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(properties.accessTokenExpiration());
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", user.accountType().name());
        claims.put("email", user.email());
        claims.put("role", user.roleName());
        claims.put("sessionVersion", user.sessionVersion());
        claims.put("authorities", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        user.tenantId().ifPresent(tenantId -> claims.put("tenantId", tenantId.toString()));
        String token = Jwts.builder()
                .subject(user.id().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claims(claims)
                .signWith(signingKey)
                .compact();
        return new TokenPair(token, expiresAt);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public ParsedToken parseToken(String token) {
        Claims claims = parseClaims(token);
        Object type = claims.get("type");
        AccountType accountType = AccountType.valueOf(type.toString());
        String tenantId = claims.get("tenantId", String.class);
        return new ParsedToken(
                UUID.fromString(claims.getSubject()),
                accountType,
                tenantId == null ? Optional.empty() : Optional.of(UUID.fromString(tenantId)),
                claims.getExpiration().toInstant()
        );
    }

    public boolean isValid(String token, CurrentUser user) {
        Claims claims = parseClaims(token);
        Object sessionVersion = claims.get("sessionVersion");
        return user.id().toString().equals(claims.getSubject())
                && user.accountType().name().equals(claims.get("type", String.class))
                && sessionVersion instanceof Number version
                && version.intValue() == user.sessionVersion()
                && user.isEnabled()
                && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        try {
            return parseClaims(token, signingKey);
        } catch (JwtException currentSecretFailure) {
            if (previousSigningKey.isEmpty()) {
                throw currentSecretFailure;
            }
            return parseClaims(token, previousSigningKey.orElseThrow());
        }
    }

    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey createSigningKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record TokenPair(String token, Instant expiresAt) {
    }

    public record ParsedToken(UUID subject, AccountType accountType, Optional<UUID> tenantId, Instant expiresAt) {
    }
}
