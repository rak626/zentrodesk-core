package com.rakesh.zentrodesk.platform.role;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserMapper;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.platform.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "active");

    private final RoleRepository roleRepository;
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
                .and((root, query, cb) -> cb.isNotNull(root.get("templateCode")))
                .and(SearchSpecificationBuilder.booleanEquals("active", active));
        return PageResponse.from(roleRepository.findAll(specification, pageable).map(roleMapper::toDto));
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        return roleMapper.toDto(getRole(id));
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        throw immutableRoles();
    }

    @Transactional
    public RoleResponse update(UUID id, RoleUpdateRequest request) {
        throw immutableRoles();
    }

    @Transactional
    public void delete(UUID id) {
        throw immutableRoles();
    }

    @Transactional
    public RoleResponse assignPermissions(UUID id, RolePermissionRequest request) {
        throw immutableRoles();
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
        return roleRepository.findWithTenantAndPermissionsByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private BadRequestException immutableRoles() {
        return new BadRequestException("Fixed roles cannot be changed");
    }
}
