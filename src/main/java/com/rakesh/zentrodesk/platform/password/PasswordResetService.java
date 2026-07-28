package com.rakesh.zentrodesk.platform.password;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.web.CorrelationIds;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.email.EmailTemplate;
import com.rakesh.zentrodesk.platform.email.TransactionalEmailService;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.token.OneTimeTokenService;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String INVALID_TOKEN = "Password reset token is invalid or expired";

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final CurrentTenant currentTenant;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokenService;
    private final TransactionalEmailService transactionalEmailService;
    private final RefreshTokenManager refreshTokenManager;
    private final AuditEventService auditEventService;

    @Value("${app.password-reset.expiration:1h}")
    private Duration resetExpiration;

    @Transactional
    public void request(PasswordResetStartRequest request) {
        User user = resolveUser(request.email(), request.tenantCode());
        if (eligible(user)) {
            issue(user);
        }
    }

    @Transactional
    public void requestForUser(UUID userId) {
        User user = userRepository.findWithTenantRoleAndPermissionsByIdAndTenantId(userId, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!eligible(user)) {
            throw new BadRequestException("Password reset is unavailable for this user");
        }
        issue(user);
    }

    @Transactional
    public void complete(PasswordResetCompleteRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashForUpdate(oneTimeTokenService.hash(request.token()))
                .orElseThrow(() -> new BadRequestException(INVALID_TOKEN));
        User user = token.getUser();
        if (token.getStatus() != PasswordResetTokenStatus.PENDING || token.getExpiresAt().isBefore(Instant.now())
                || !eligible(user)) {
            throw new BadRequestException(INVALID_TOKEN);
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setSessionVersion(user.getSessionVersion() + 1);
        token.setStatus(PasswordResetTokenStatus.CONSUMED);
        token.setConsumedAt(Instant.now());
        passwordResetTokenRepository.findAllByUserIdAndStatus(user.getId(), PasswordResetTokenStatus.PENDING).stream()
                .filter(other -> !other.getId().equals(token.getId()))
                .forEach(other -> other.setStatus(PasswordResetTokenStatus.REVOKED));
        refreshTokenManager.revokeAccount(AccountType.TENANT, user.getId());
        auditEventService.record(token.getTenant().getId(), user.getId(), "PASSWORD_RESET", token.getId(),
                "COMPLETE", CorrelationIds.currentOrGenerated(), existingSummary(token.getId()),
                successSummary(token.getId()));
    }

    private void issue(User user) {
        passwordResetTokenRepository.findAllByUserIdAndStatus(user.getId(), PasswordResetTokenStatus.PENDING)
                .forEach(existing -> existing.setStatus(PasswordResetTokenStatus.REVOKED));
        OneTimeTokenService.IssuedToken issued = oneTimeTokenService.issue();
        PasswordResetToken token = passwordResetTokenRepository.save(PasswordResetToken.builder()
                .tenant(user.getTenant())
                .user(user)
                .tokenHash(issued.hash())
                .status(PasswordResetTokenStatus.PENDING)
                .expiresAt(Instant.now().plus(resetExpiration))
                .build());
        transactionalEmailService.enqueue(user.getTenant().getId(), user.getEmail(), EmailTemplate.PASSWORD_RESET,
                token.getId(), issued.token());
        auditEventService.record(user.getTenant().getId(), user.getId(), "PASSWORD_RESET", token.getId(),
                "REQUEST", CorrelationIds.currentOrGenerated(), null, successSummary(token.getId()));
    }

    private User resolveUser(String email, String tenantCode) {
        if (StringUtils.hasText(tenantCode)) {
            return userRepository.findWithTenantRoleAndPermissionsByEmailAndTenantCode(email, tenantCode).orElse(null);
        }
        List<User> matches = userRepository.findAllWithTenantRoleAndPermissionsByEmail(email);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private boolean eligible(User user) {
        return user != null && user.isActive() && user.getTenant().isActive() && user.getRole().isActive();
    }

    private String existingSummary(UUID id) {
        return "{\"state\":\"EXISTING\",\"entityId\":\"" + id + "\"}";
    }

    private String successSummary(UUID id) {
        return "{\"outcome\":\"SUCCESS\",\"entityId\":\"" + id + "\"}";
    }
}
