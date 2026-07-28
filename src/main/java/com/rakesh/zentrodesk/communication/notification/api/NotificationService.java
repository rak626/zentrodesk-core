package com.rakesh.zentrodesk.communication.notification.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.communication.notification.Notification;
import com.rakesh.zentrodesk.communication.notification.NotificationRepository;
import com.rakesh.zentrodesk.communication.notification.NotificationStatus;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt", "updatedAt", "title", "status", "category", "active"
    );
    private static final List<String> ALLOWED_DEEP_LINK_PREFIXES = List.of(
            "/dashboard", "/leads", "/bookings", "/availability", "/drivers", "/reports"
    );

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            NotificationStatus status,
            String category,
            CurrentUser user
    ) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Notification> spec = Specification
                .where(SearchSpecificationBuilder.<Notification>uuidEquals("tenantId", user.tenantId().orElseThrow()))
                .and(SearchSpecificationBuilder.uuidEquals("userId", user.id()))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> category == null || category.isBlank()
                        ? cb.conjunction()
                        : cb.equal(root.get("category"), category.trim().toUpperCase()));
        return PageResponse.from(repository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser user) {
        return repository.countByTenantIdAndUserIdAndStatusAndActiveTrue(
                user.tenantId().orElseThrow(),
                user.id(),
                NotificationStatus.UNREAD
        );
    }

    @Transactional
    @AuditMutation(entityType = "NOTIFICATION", action = "MARK_READ")
    public NotificationResponse markRead(UUID id, @AuthenticationPrincipal CurrentUser user) {
        Notification notification = repository.findByIdAndTenantIdAndUserId(
                        id,
                        user.tenantId().orElseThrow(),
                        user.id()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setStatus(NotificationStatus.READ);
        return toResponse(notification);
    }

    @Transactional
    @AuditMutation(entityType = "NOTIFICATION", action = "MARK_ALL_READ")
    public int markAllRead(CurrentUser user) {
        return repository.markAllRead(user.tenantId().orElseThrow(), user.id());
    }

    @Transactional
    public void createIdempotent(
            UUID tenantId,
            UUID recipientId,
            String eventKey,
            String category,
            String title,
            String message,
            String deepLink,
            String sourceType,
            UUID sourceId
    ) {
        if (repository.findByTenantIdAndUserIdAndEventKey(tenantId, recipientId, eventKey).isPresent()) {
            return;
        }
        try {
            repository.saveAndFlush(Notification.builder()
                    .tenantId(tenantId)
                    .userId(recipientId)
                    .eventKey(eventKey)
                    .category(category.trim().toUpperCase())
                    .title(title)
                    .message(message)
                    .deepLink(validatedDeepLink(deepLink))
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .status(NotificationStatus.UNREAD)
                    .active(true)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // A retried or concurrent event already created this recipient notification.
        }
    }

    private String validatedDeepLink(String deepLink) {
        if (deepLink == null || deepLink.isBlank()) {
            return null;
        }
        String candidate = deepLink.trim();
        boolean allowed = candidate.startsWith("/")
                && !candidate.startsWith("//")
                && ALLOWED_DEEP_LINK_PREFIXES.stream().anyMatch(prefix ->
                candidate.equals(prefix)
                        || candidate.startsWith(prefix + "/")
                        || candidate.startsWith(prefix + "?")
                        || candidate.startsWith(prefix + "#"));
        return allowed ? candidate : null;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCategory(),
                notification.getDeepLink(),
                notification.getSourceType(),
                notification.getSourceId(),
                notification.getStatus(),
                notification.isActive(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}
