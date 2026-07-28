package com.rakesh.zentrodesk.platform.invitation;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.common.web.CorrelationIds;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.email.EmailTemplate;
import com.rakesh.zentrodesk.platform.email.TransactionalEmailService;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.role.RoleRepository;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.token.OneTimeTokenService;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserCreateRequest;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "expiresAt", "email", "status");
    private static final String INVALID_INVITATION = "Invitation is invalid or expired";

    private final TenantInvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CurrentTenant currentTenant;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokenService;
    private final TransactionalEmailService transactionalEmailService;
    private final AuditEventService auditEventService;

    @Value("${app.invitation.expiration:72h}")
    private Duration invitationExpiration;

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            UUID roleId,
            InvitationStatus status
    ) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Instant now = Instant.now();
        Specification<TenantInvitation> specification = Specification
                .where(SearchSpecificationBuilder.<TenantInvitation>uuidEquals("tenant.id", currentTenant.id()))
                .and(SearchSpecificationBuilder.textContains(search, Set.of("email", "user.firstName", "user.lastName")))
                .and(SearchSpecificationBuilder.uuidEquals("role.id", roleId))
                .and(statusSpecification(status, now));
        return PageResponse.from(invitationRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional
    @AuditMutation(entityType = "INVITATION", action = "CREATE")
    public InvitationResponse invite(UserCreateRequest request) {
        UUID tenantId = currentTenant.id();
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        requireAvailableSeat(tenant);
        Role role = roleRepository.findWithTenantAndPermissionsByIdAndTenantId(request.roleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        User user = createPendingUser(tenant, role, request.firstName(), request.lastName(), request.email(), request.phone());
        return toResponse(issue(tenant, role, user, currentTenant.userId()));
    }

    @Transactional
    public InvitationResponse inviteOwner(
            Tenant tenant,
            Role ownerRole,
            String firstName,
            String lastName,
            String email,
            String phone,
            UUID invitedBy
    ) {
        requireAvailableSeat(tenant);
        User user = createPendingUser(tenant, ownerRole, firstName, lastName, email, phone);
        TenantInvitation invitation = issue(tenant, ownerRole, user, invitedBy);
        auditEventService.record(tenant.getId(), invitedBy, "INVITATION", invitation.getId(), "CREATE",
                CorrelationIds.currentOrGenerated(), null, successSummary(invitation.getId()));
        return toResponse(invitation);
    }

    @Transactional
    @AuditMutation(entityType = "INVITATION", action = "RESEND")
    public InvitationResponse resend(UUID id) {
        TenantInvitation invitation = getInvitationForUpdate(id);
        requireResendable(invitation);
        boolean occupiesSeat = invitation.getExpiresAt().isAfter(Instant.now());
        Tenant tenant = tenantRepository.findByIdForUpdate(invitation.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (!occupiesSeat) {
            requireAvailableSeat(tenant);
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        return toResponse(issue(tenant, invitation.getRole(), invitation.getUser(), currentTenant.userId()));
    }

    @Transactional
    @AuditMutation(entityType = "INVITATION", action = "REVOKE")
    public InvitationResponse revoke(UUID id) {
        TenantInvitation invitation = getInvitationForUpdate(id);
        requireRevocable(invitation);
        invitation.setStatus(InvitationStatus.REVOKED);
        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public InvitationDetailsResponse validate(InvitationTokenRequest request) {
        TenantInvitation invitation = invitationRepository.findByTokenHash(oneTimeTokenService.hash(request.token()))
                .orElseThrow(() -> new BadRequestException(INVALID_INVITATION));
        requireAcceptable(invitation);
        return new InvitationDetailsResponse(invitation.getEmail(), invitation.getUser().getFirstName(),
                invitation.getTenant().getName(), invitation.getRole().getName(), invitation.getExpiresAt());
    }

    @Transactional
    public void accept(InvitationAcceptRequest request) {
        TenantInvitation invitation = invitationRepository.findByTokenHashForUpdate(oneTimeTokenService.hash(request.token()))
                .orElseThrow(() -> new BadRequestException(INVALID_INVITATION));
        requireAcceptable(invitation);
        User user = invitation.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.findAllByUserIdAndStatus(user.getId(), InvitationStatus.PENDING).stream()
                .filter(other -> !other.getId().equals(invitation.getId()))
                .forEach(other -> other.setStatus(InvitationStatus.REVOKED));
        auditEventService.record(invitation.getTenant().getId(), user.getId(), "INVITATION", invitation.getId(),
                "ACCEPT", CorrelationIds.currentOrGenerated(), existingSummary(invitation.getId()),
                successSummary(invitation.getId()));
    }

    @Transactional
    public void revokePendingForUser(UUID userId) {
        invitationRepository.findAllByUserIdAndStatus(userId, InvitationStatus.PENDING)
                .forEach(invitation -> invitation.setStatus(InvitationStatus.REVOKED));
    }

    private User createPendingUser(
            Tenant tenant,
            Role role,
            String firstName,
            String lastName,
            String email,
            String phone
    ) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByTenantAndEmailExcludingUser(tenant.getId(), normalizedEmail, null)) {
            throw new BadRequestException("Email already exists for this tenant");
        }
        return userRepository.save(User.builder()
                .tenant(tenant)
                .role(role)
                .firstName(firstName.trim())
                .lastName(trimToNull(lastName))
                .email(normalizedEmail)
                .phone(trimToNull(phone))
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .active(false)
                .build());
    }

    private void requireAvailableSeat(Tenant tenant) {
        long occupiedSeats = userRepository.countOccupiedSeats(tenant.getId(), Instant.now());
        if (occupiedSeats >= tenant.getSeatLimit()) {
            throw new BadRequestException("Tenant seat limit reached");
        }
    }

    private TenantInvitation issue(Tenant tenant, Role role, User user, UUID invitedBy) {
        OneTimeTokenService.IssuedToken token = oneTimeTokenService.issue();
        TenantInvitation invitation = invitationRepository.save(TenantInvitation.builder()
                .tenant(tenant)
                .user(user)
                .role(role)
                .invitedBy(invitedBy)
                .email(user.getEmail())
                .tokenHash(token.hash())
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(invitationExpiration))
                .build());
        transactionalEmailService.enqueue(tenant.getId(), user.getEmail(), EmailTemplate.TENANT_INVITATION,
                invitation.getId(), token.token());
        return invitation;
    }

    private TenantInvitation getInvitationForUpdate(UUID id) {
        return invitationRepository.findByIdAndTenantIdForUpdate(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
    }

    private void requirePending(TenantInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())
                || invitation.getUser().isActive()) {
            throw new BadRequestException(INVALID_INVITATION);
        }
    }

    private void requireResendable(TenantInvitation invitation) {
        requireRevocable(invitation);
    }

    private void requireRevocable(TenantInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getUser().isActive()) {
            throw new BadRequestException(INVALID_INVITATION);
        }
    }

    private void requireAcceptable(TenantInvitation invitation) {
        requirePending(invitation);
        if (!invitation.getTenant().isActive() || !invitation.getRole().isActive()) {
            throw new BadRequestException(INVALID_INVITATION);
        }
    }

    private InvitationResponse toResponse(TenantInvitation invitation) {
        InvitationStatus status = invitation.getStatus() == InvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(Instant.now()) ? InvitationStatus.EXPIRED : invitation.getStatus();
        return new InvitationResponse(
                invitation.getId(),
                invitation.getUser().getId(),
                invitation.getRole().getId(),
                invitation.getRole().getName(),
                invitation.getUser().getFirstName(),
                invitation.getUser().getLastName(),
                invitation.getEmail(),
                status,
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
    }

    private Specification<TenantInvitation> statusSpecification(InvitationStatus status, Instant now) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            if (status == InvitationStatus.EXPIRED) {
                return cb.and(
                        cb.equal(root.get("status"), InvitationStatus.PENDING),
                        cb.lessThan(root.get("expiresAt"), now)
                );
            }
            if (status == InvitationStatus.PENDING) {
                return cb.and(
                        cb.equal(root.get("status"), InvitationStatus.PENDING),
                        cb.greaterThanOrEqualTo(root.get("expiresAt"), now)
                );
            }
            return cb.equal(root.get("status"), status);
        };
    }

    private String existingSummary(UUID id) {
        return "{\"state\":\"EXISTING\",\"entityId\":\"" + id + "\"}";
    }

    private String successSummary(UUID id) {
        return "{\"outcome\":\"SUCCESS\",\"entityId\":\"" + id + "\"}";
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
