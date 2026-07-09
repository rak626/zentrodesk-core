package com.rakesh.zentrodesk.platform.user;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.role.Role;
import com.rakesh.zentrodesk.platform.role.RoleRepository;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;
    private final CurrentTenant currentTenant;

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

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        Role role = getRole(request.roleId());
        validateRoleTenant(role, tenant.getId());
        validateUniqueEmail(tenant.getId(), request.email(), null);

        User user = User.builder()
                .tenant(tenant)
                .role(role)
                .firstName(request.firstName().trim())
                .lastName(trimToNull(request.lastName()))
                .email(request.email().trim().toLowerCase())
                .phone(trimToNull(request.phone()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(request.active() == null || request.active())
                .build();
        log.info("Creating user {}", user.getEmail());
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        Role role = getRole(request.roleId());
        validateRoleTenant(role, tenant.getId());
        validateUniqueEmail(tenant.getId(), request.email(), id);

        user.setTenant(tenant);
        user.setRole(role);
        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(trimToNull(request.phone()));
        if (request.active() != null) {
            user.setActive(request.active());
        }
        return userMapper.toDto(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        user.setActive(false);
        log.info("Disabled user {} through delete endpoint", user.getEmail());
    }

    @Transactional
    public UserResponse enable(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        user.setActive(true);
        return userMapper.toDto(user);
    }

    @Transactional
    public UserResponse disable(UUID id) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        user.setActive(false);
        return userMapper.toDto(user);
    }

    @Transactional
    public UserResponse assignRole(UUID id, UserRoleAssignmentRequest request) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        Role role = getRole(request.roleId());
        validateRoleTenant(role, user.getTenant().getId());
        user.setRole(role);
        return userMapper.toDto(user);
    }

    @Transactional
    public void resetPassword(UUID id, PasswordResetRequest request) {
        User user = getUser(id);
        currentTenant.require(user.getTenant().getId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        log.info("Password reset for user {}", user.getEmail());
    }

    private User getUser(UUID id) {
        User user = userRepository.findWithTenantRoleAndPermissionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        currentTenant.require(user.getTenant().getId());
        return user;
    }

    private Tenant getTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private Role getRole(UUID id) {
        return roleRepository.findWithTenantAndPermissionsById(id)
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

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
