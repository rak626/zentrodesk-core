package com.rakesh.zentrodesk.communication.notification.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.communication.notification.Notification;
import com.rakesh.zentrodesk.communication.notification.NotificationRepository;
import com.rakesh.zentrodesk.communication.notification.NotificationStatus;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "title", "status", "active");

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findAll(int page, int size, String sort, String direction, NotificationStatus status, CurrentUser user) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Notification> spec = Specification
                .where(SearchSpecificationBuilder.<Notification>uuidEquals("userId", user.id()))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        return PageResponse.from(repository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional
    public NotificationResponse markRead(UUID id, @AuthenticationPrincipal CurrentUser user) {
        Notification notification = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUserId().equals(user.id())) {
            throw new ResourceNotFoundException("Notification not found");
        }
        notification.setStatus(NotificationStatus.READ);
        return toResponse(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(), notification.getTitle(), notification.getMessage(),
                notification.getStatus(), notification.isActive(), notification.getCreatedAt(), notification.getUpdatedAt());
    }
}
