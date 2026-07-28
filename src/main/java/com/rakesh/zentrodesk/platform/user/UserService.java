package com.rakesh.zentrodesk.platform.user;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.invitation.InvitationResponse;
import com.rakesh.zentrodesk.platform.invitation.InvitationService;
import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.role.RoleRepository;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt", "updatedAt", "firstName", "lastName", "email", "active", "lastLoginAt"
    );

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final CurrentTenant currentTenant;
    private final RefreshTokenManager refreshTokenManager;
    private final InvitationService invitationService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(int page, int size, String sort, String direction,
                                              String search, UUID roleId, Boolean active) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        UUID effectiveTenantId = currentTenant.id();
        Specification<User> specification = Specification
                .where(SearchSpecificationBuilder.<User>textContains(search, Set.of("firstName", "lastName", "email", "phone")))
                .and(SearchSpecificationBuilder.uuidEquals("tenant.id", effectiveTenantId))
                .and(SearchSpecificationBuilder.uuidEquals("role.id", roleId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active));
        return PageResponse.from(userRepository.findAll(specification, pageable).map(userMapper::toDto));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return userMapper.toDto(getUser(id));
    }

    @Transactional(readOnly = true)
    public TeamSummaryResponse summary() {
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        long occupiedSeats = userRepository.countOccupiedSeats(tenantId, Instant.now());
        return new TeamSummaryResponse(
                tenant.getSeatLimit(),
                occupiedSeats,
                userRepository.countByTenantIdAndActiveTrue(tenantId),
                Math.max(tenant.getSeatLimit() - occupiedSeats, 0)
        );
    }

    public InvitationResponse create(UserCreateRequest request) {
        return invitationService.invite(request);
    }

    @Transactional
    @AuditMutation(entityType = "USER", action = "UPDATE")
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        Role role = getRole(request.roleId());
        validateRoleTenant(role, tenant.getId());
        validateUniqueEmail(tenant.getId(), request.email(), id);
        validateRoleChange(user, role);
        validateActiveChange(user, request.active());
        if (Boolean.TRUE.equals(request.active()) && !user.isActive()) {
            requireAvailableSeat(getTenantForUpdate(tenantId));
        }

        user.setTenant(tenant);
        user.setRole(role);
        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(trimToNull(request.phone()));
        if (request.active() != null) {
            user.setActive(request.active());
            if (!request.active()) {
                user.setSessionVersion(user.getSessionVersion() + 1);
                refreshTokenManager.revokeAccount(AccountType.TENANT, user.getId());
                invitationService.revokePendingForUser(user.getId());
            }
        }
        return userMapper.toDto(user);
    }

    @Transactional
    @AuditMutation(entityType = "USER", action = "DISABLE")
    public void delete(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        requireCanDeactivate(user);
        user.setActive(false);
        user.setSessionVersion(user.getSessionVersion() + 1);
        refreshTokenManager.revokeAccount(AccountType.TENANT, user.getId());
        invitationService.revokePendingForUser(user.getId());
        log.info("Disabled user {} through delete endpoint", user.getEmail());
    }

    @Transactional
    @AuditMutation(entityType = "USER", action = "ENABLE")
    public UserResponse enable(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        if (!user.isActive()) {
            requireAvailableSeat(getTenantForUpdate(user.getTenant().getId()));
        }
        user.setActive(true);
        return userMapper.toDto(user);
    }

    @Transactional
    @AuditMutation(entityType = "USER", action = "DISABLE")
    public UserResponse disable(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        requireCanDeactivate(user);
        user.setActive(false);
        user.setSessionVersion(user.getSessionVersion() + 1);
        refreshTokenManager.revokeAccount(AccountType.TENANT, user.getId());
        invitationService.revokePendingForUser(user.getId());
        return userMapper.toDto(user);
    }

    @Transactional
    @AuditMutation(entityType = "USER", action = "ROLE_ASSIGN")
    public UserResponse assignRole(UUID id, UserRoleAssignmentRequest request) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        Role role = getRole(request.roleId());
        validateRoleTenant(role, user.getTenant().getId());
        validateRoleChange(user, role);
        user.setRole(role);
        return userMapper.toDto(user);
    }

    @Transactional
    @AuditMutation(entityType = "USER", action = "SESSION_REVOKE")
    public void revokeSessions(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        user.setSessionVersion(user.getSessionVersion() + 1);
        refreshTokenManager.revokeAccount(AccountType.TENANT, user.getId());
    }

    private User getUser(UUID id) {
        return userRepository.findWithTenantRoleAndPermissionsByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Tenant getTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private Tenant getTenantForUpdate(UUID id) {
        return tenantRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private Role getRole(UUID id) {
        return roleRepository.findWithTenantAndPermissionsByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private void validateRoleTenant(Role role, UUID tenantId) {
        if (!role.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException("Role does not belong to the selected tenant");
        }
    }

    private void validateUniqueEmail(UUID tenantId, String email, UUID excludedUserId) {
        if (userRepository.existsByTenantAndEmailExcludingUser(tenantId, email, excludedUserId)) {
            throw new BadRequestException("Email already exists for this tenant");
        }
    }

    private void validateActiveChange(User user, Boolean active) {
        if (Boolean.FALSE.equals(active) && user.isActive()) {
            requireCanDeactivate(user);
        }
    }

    private void validateRoleChange(User user, Role newRole) {
        if (user.getRole().getId().equals(newRole.getId())) {
            return;
        }
        if (user.getRole().getTemplateCode() == FixedTenantRole.OWNER
                && newRole.getTemplateCode() != FixedTenantRole.OWNER) {
            requireAnotherActiveOwner(user.getTenant().getId());
        }
    }

    private void requireCanDeactivate(User user) {
        if (user.getId().equals(currentTenant.userId())) {
            throw new BadRequestException("You cannot disable your own account");
        }
        if (user.isActive() && user.getRole().getTemplateCode() == FixedTenantRole.OWNER) {
            requireAnotherActiveOwner(user.getTenant().getId());
        }
    }

    private void requireAnotherActiveOwner(UUID tenantId) {
        if (userRepository.countByTenantIdAndRoleTemplateCodeAndActiveTrue(tenantId, FixedTenantRole.OWNER) <= 1) {
            throw new BadRequestException("The last active owner cannot be removed");
        }
    }

    private void requireAvailableSeat(Tenant tenant) {
        if (userRepository.countOccupiedSeats(tenant.getId(), Instant.now()) >= tenant.getSeatLimit()) {
            throw new BadRequestException("Tenant seat limit reached");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
