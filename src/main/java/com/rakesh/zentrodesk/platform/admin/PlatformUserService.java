package com.rakesh.zentrodesk.platform.admin;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.user.PasswordResetRequest;
import com.rakesh.zentrodesk.platform.user.PlatformRole;
import com.rakesh.zentrodesk.platform.user.PlatformUser;
import com.rakesh.zentrodesk.platform.user.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt", "updatedAt", "firstName", "lastName", "email", "role", "enabled", "lastLoginAt"
    );

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<PlatformUserResponse> findAll(int page, int size, String sort, String direction,
                                                      String search, PlatformRole role, Boolean enabled) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<PlatformUser> specification = Specification
                .where(SearchSpecificationBuilder.<PlatformUser>textContains(search, Set.of("firstName", "lastName", "email")))
                .and((root, query, cb) -> role == null ? cb.conjunction() : cb.equal(root.get("role"), role))
                .and(SearchSpecificationBuilder.booleanEquals("enabled", enabled));
        return PageResponse.from(platformUserRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PlatformUserResponse findById(UUID id) {
        return toResponse(getUser(id));
    }

    @Transactional
    public PlatformUserResponse create(PlatformUserCreateRequest request) {
        requireSuperAdmin();
        validateUniqueEmail(request.email(), null);

        PlatformUser user = PlatformUser.builder()
                .firstName(request.firstName().trim())
                .lastName(trimToNull(request.lastName()))
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(request.enabled() == null || request.enabled())
                .build();
        return toResponse(platformUserRepository.save(user));
    }

    @Transactional
    public PlatformUserResponse update(UUID id, PlatformUserUpdateRequest request) {
        requireSuperAdmin();
        PlatformUser user = getUser(id);
        validateUniqueEmail(request.email(), id);
        preventLastSuperAdminLoss(user, request.role(), request.enabled());

        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setEmail(request.email().trim().toLowerCase());
        user.setRole(request.role());
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        return toResponse(user);
    }

    @Transactional
    public PlatformUserResponse enable(UUID id) {
        requireSuperAdmin();
        PlatformUser user = getUser(id);
        user.setEnabled(true);
        return toResponse(user);
    }

    @Transactional
    public PlatformUserResponse disable(UUID id) {
        requireSuperAdmin();
        PlatformUser user = getUser(id);
        preventLastSuperAdminLoss(user, user.getRole(), false);
        user.setEnabled(false);
        return toResponse(user);
    }

    @Transactional
    public void resetPassword(UUID id, PasswordResetRequest request) {
        requireSuperAdmin();
        PlatformUser user = getUser(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
    }

    private void requireSuperAdmin() {
        if (PlatformAccess.currentRole() != PlatformRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Platform user management requires super admin access");
        }
    }

    private PlatformUser getUser(UUID id) {
        return platformUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Platform user not found"));
    }

    private void validateUniqueEmail(String email, UUID excludedUserId) {
        if (platformUserRepository.existsByEmailExcludingUser(email.trim(), excludedUserId)) {
            throw new BadRequestException("Email already exists for a platform user");
        }
    }

    private void preventLastSuperAdminLoss(PlatformUser user, PlatformRole nextRole, Boolean nextEnabled) {
        boolean currentlyEnabledSuperAdmin = user.isEnabled() && user.getRole() == PlatformRole.SUPER_ADMIN;
        boolean remainsEnabledSuperAdmin = (nextEnabled == null || nextEnabled) && nextRole == PlatformRole.SUPER_ADMIN;
        if (currentlyEnabledSuperAdmin && !remainsEnabledSuperAdmin
                && platformUserRepository.countByRoleAndEnabledTrue(PlatformRole.SUPER_ADMIN) <= 1) {
            throw new BadRequestException("Cannot disable or demote the last enabled super admin");
        }
    }

    private PlatformUserResponse toResponse(PlatformUser user) {
        return new PlatformUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getLastLoginAt(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
