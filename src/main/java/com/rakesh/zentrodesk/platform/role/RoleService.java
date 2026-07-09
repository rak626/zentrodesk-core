package com.rakesh.zentrodesk.platform.role;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.Tenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserMapper;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.platform.user.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "active");

    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> findAll(int page, int size, String sort, String direction,
                                              String search, Boolean active) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        UUID effectiveTenantId = currentTenant.id();
        Specification<Role> specification = Specification
                .where(SearchSpecificationBuilder.<Role>textContains(search, Set.of("name", "description")))
                .and(SearchSpecificationBuilder.uuidEquals("tenant.id", effectiveTenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active));
        return PageResponse.from(roleRepository.findAll(specification, pageable).map(roleMapper::toDto));
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        return roleMapper.toDto(getRole(id));
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        validateUniqueName(tenant.getId(), request.name(), null);
        Role role = Role.builder()
                .tenant(tenant)
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .active(request.active() == null || request.active())
                .permissions(normalizePermissions(request.permissions()))
                .build();
        log.info("Creating role {} for tenant {}", role.getName(), tenant.getCode());
        return roleMapper.toDto(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(UUID id, RoleUpdateRequest request) {
        Role role = getRole(id);
        UUID tenantId = currentTenant.id();
        Tenant tenant = getTenant(tenantId);
        validateUniqueName(tenant.getId(), request.name(), id);
        role.setTenant(tenant);
        role.setName(request.name().trim());
        role.setDescription(trimToNull(request.description()));
        if (request.active() != null) {
            role.setActive(request.active());
        }
        if (request.permissions() != null) {
            role.getPermissions().clear();
            role.getPermissions().addAll(normalizePermissions(request.permissions()));
        }
        return roleMapper.toDto(role);
    }

    @Transactional
    public void delete(UUID id) {
        Role role = getRole(id);
        role.setActive(false);
        log.info("Disabled role {}", role.getName());
    }

    @Transactional
    public RoleResponse assignPermissions(UUID id, RolePermissionRequest request) {
        Role role = getRole(id);
        role.getPermissions().clear();
        role.getPermissions().addAll(normalizePermissions(request.permissions()));
        return roleMapper.toDto(role);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> users(UUID roleId, int page, int size, String sort, String direction, Boolean active) {
        getRole(roleId);
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, Set.of(
                "createdAt", "updatedAt", "firstName", "lastName", "email", "active", "lastLoginAt"
        ));
        Specification<User> specification = Specification
                .where(SearchSpecificationBuilder.<User>uuidEquals("role.id", roleId))
                .and(SearchSpecificationBuilder.uuidEquals("tenant.id", currentTenant.id()))
                .and(SearchSpecificationBuilder.booleanEquals("active", active));
        return PageResponse.from(userRepository.findAll(specification, pageable).map(userMapper::toDto));
    }

    private Role getRole(UUID id) {
        Role role = roleRepository.findWithTenantAndPermissionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        currentTenant.require(role.getTenant().getId());
        return role;
    }

    private Tenant getTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private void validateUniqueName(UUID tenantId, String name, UUID excludedRoleId) {
        if (roleRepository.existsByTenantAndNameExcludingRole(tenantId, name, excludedRoleId)) {
            throw new BadRequestException("Role name already exists for this tenant");
        }
    }

    private Set<String> normalizePermissions(Set<String> permissions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (permissions == null) {
            return normalized;
        }
        permissions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toUpperCase)
                .forEach(normalized::add);
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
