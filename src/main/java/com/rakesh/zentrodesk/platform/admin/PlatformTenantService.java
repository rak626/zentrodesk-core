package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.common.web.CorrelationIds;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.platform.invitation.InvitationService;
import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.role.RoleRepository;
import com.rakesh.zentrodesk.platform.tenant.SubscriptionStatus;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformTenantService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt", "updatedAt", "name", "code", "email", "city", "active"
    );

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenManager refreshTokenManager;
    private final InvitationService invitationService;
    private final UserRepository userRepository;
    private final AuditEventService auditEventService;

    @Transactional(readOnly = true)
    public PageResponse<PlatformTenantResponse> findAll(int page, int size, String sort, String direction, String search, Boolean active) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Tenant> specification = Specification
                .where(SearchSpecificationBuilder.<Tenant>textContains(search, Set.of("name", "code", "email", "phone", "city")))
                .and(SearchSpecificationBuilder.booleanEquals("active", active));
        return PageResponse.from(tenantRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PlatformTenantResponse findById(UUID id) {
        return toResponse(getTenant(id));
    }

    @Transactional
    public PlatformTenantResponse create(PlatformTenantCreateRequest request) {
        requireTenantManager();
        validateUniqueCode(request.code(), null);

        Tenant tenant = Tenant.builder()
                .name(request.name().trim())
                .code(request.code().trim().toUpperCase())
                .email(trimToNull(request.email()))
                .phone(trimToNull(request.phone()))
                .address(trimToNull(request.address()))
                .city(trimToNull(request.city()))
                .state(trimToNull(request.state()))
                .country(trimToNull(request.country()))
                .timezone(trimToNull(request.timezone()))
                .currency(trimToNull(request.currency()))
                .active(true)
                .subscriptionStatus(request.subscriptionStatus() == null
                        ? SubscriptionStatus.TRIAL : request.subscriptionStatus())
                .planCode(normalizePlanCode(request.planCode()))
                .seatLimit(request.seatLimit() == null ? 5 : request.seatLimit())
                .build();
        Tenant savedTenant = tenantRepository.save(tenant);

        Role ownerRole = null;
        for (FixedTenantRole template : FixedTenantRole.values()) {
            Role role = roleRepository.save(Role.builder()
                    .tenant(savedTenant)
                    .name(template.displayName())
                    .description(template.description())
                    .templateCode(template)
                    .active(true)
                    .permissions(template.permissions())
                    .build());
            if (template == FixedTenantRole.OWNER) {
                ownerRole = role;
            }
        }

        invitationService.inviteOwner(savedTenant, ownerRole, request.adminFirstName(), request.adminLastName(),
                request.adminEmail(), request.adminPhone(), PlatformAccess.currentUserId());
        recordAudit(savedTenant, "PROVISION", null);

        return toResponse(savedTenant);
    }

    @Transactional
    public PlatformTenantResponse update(UUID id, PlatformTenantUpdateRequest request) {
        requireTenantManager();
        Tenant tenant = getTenantForUpdate(id);
        validateUniqueCode(request.code(), id);
        String beforeSummary = provisioningSummary(tenant);
        int seatLimit = request.seatLimit() == null ? tenant.getSeatLimit() : request.seatLimit();
        validateSeatLimit(tenant.getId(), seatLimit);

        tenant.setName(request.name().trim());
        tenant.setCode(request.code().trim().toUpperCase());
        tenant.setEmail(trimToNull(request.email()));
        tenant.setPhone(trimToNull(request.phone()));
        tenant.setAddress(trimToNull(request.address()));
        tenant.setCity(trimToNull(request.city()));
        tenant.setState(trimToNull(request.state()));
        tenant.setCountry(trimToNull(request.country()));
        tenant.setTimezone(trimToNull(request.timezone()));
        tenant.setCurrency(trimToNull(request.currency()));
        tenant.setSubscriptionStatus(request.subscriptionStatus() == null
                ? tenant.getSubscriptionStatus() : request.subscriptionStatus());
        tenant.setPlanCode(request.planCode() == null ? tenant.getPlanCode() : normalizePlanCode(request.planCode()));
        tenant.setSeatLimit(seatLimit);
        if (request.active() != null) {
            tenant.setActive(request.active());
            if (!request.active()) {
                refreshTokenManager.revokeTenant(tenant.getId());
            }
        }
        recordAudit(tenant, "PROVISIONING_UPDATE", beforeSummary);
        return toResponse(tenant);
    }

    @Transactional
    public PlatformTenantResponse enable(UUID id) {
        requireTenantManager();
        Tenant tenant = getTenant(id);
        tenant.setActive(true);
        recordAudit(tenant, "RESUME", null);
        return toResponse(tenant);
    }

    @Transactional
    public PlatformTenantResponse disable(UUID id) {
        requireTenantManager();
        Tenant tenant = getTenant(id);
        tenant.setActive(false);
        refreshTokenManager.revokeTenant(tenant.getId());
        recordAudit(tenant, "SUSPEND", null);
        return toResponse(tenant);
    }

    private void requireTenantManager() {
        PlatformRole role = PlatformAccess.currentRole();
        if (!PlatformAccess.canManageTenants(role)) {
            throw new AccessDeniedException("Tenant management requires platform admin access");
        }
    }

    private Tenant getTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private Tenant getTenantForUpdate(UUID id) {
        return tenantRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private void validateUniqueCode(String code, UUID excludedTenantId) {
        if (tenantRepository.existsByCodeExcludingTenant(code.trim(), excludedTenantId)) {
            throw new BadRequestException("Tenant code already exists");
        }
    }

    private PlatformTenantResponse toResponse(Tenant tenant) {
        return new PlatformTenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getCode(),
                tenant.getEmail(),
                tenant.getPhone(),
                tenant.getAddress(),
                tenant.getCity(),
                tenant.getState(),
                tenant.getCountry(),
                tenant.getTimezone(),
                tenant.getCurrency(),
                tenant.isActive(),
                tenant.getSubscriptionStatus(),
                tenant.getPlanCode(),
                tenant.getSeatLimit(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizePlanCode(String planCode) {
        return planCode == null || planCode.isBlank()
                ? "STARTER"
                : planCode.trim().toUpperCase(Locale.ROOT);
    }

    private void validateSeatLimit(UUID tenantId, int seatLimit) {
        long occupiedSeats = userRepository.countOccupiedSeats(tenantId, Instant.now());
        if (seatLimit < occupiedSeats) {
            throw new BadRequestException("Seat limit cannot be lower than occupied seats");
        }
    }

    private void recordAudit(Tenant tenant, String action, String beforeSummary) {
        auditEventService.record(
                tenant.getId(),
                PlatformAccess.currentUserId(),
                "TENANT",
                tenant.getId(),
                action,
                CorrelationIds.currentOrGenerated(),
                beforeSummary,
                provisioningSummary(tenant)
        );
    }

    private String provisioningSummary(Tenant tenant) {
        return "{\"subscriptionStatus\":\"" + tenant.getSubscriptionStatus()
                + "\",\"planCode\":\"" + tenant.getPlanCode()
                + "\",\"seatLimit\":" + tenant.getSeatLimit()
                + ",\"active\":" + tenant.isActive() + "}";
    }
}
