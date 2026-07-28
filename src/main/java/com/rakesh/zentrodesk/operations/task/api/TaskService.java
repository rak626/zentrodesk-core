package com.rakesh.zentrodesk.operations.task.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.task.Task;
import com.rakesh.zentrodesk.operations.task.TaskRepository;
import com.rakesh.zentrodesk.operations.task.TaskStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "title", "dueAt", "status", "active");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> findAll(int page, int size, String sort, String direction, TaskStatus status, UUID userId, Instant from, Instant to) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Task> spec = Specification
                .where(SearchSpecificationBuilder.<Task>uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("userId", userId))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status))
                .and((root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("dueAt"), from))
                .and((root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("dueAt"), to));
        return PageResponse.from(taskRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional
    @AuditMutation(entityType = "TASK", action = "CREATE")
    public TaskResponse create(TaskRequest request) {
        UUID tenantId = currentTenant.id();
        validateUser(request.userId(), tenantId);
        Task task = Task.builder()
                .tenantId(tenantId)
                .userId(request.userId())
                .title(request.title().trim())
                .description(trimToNull(request.description()))
                .dueAt(request.dueAt())
                .status(request.status() == null ? TaskStatus.PENDING : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    @AuditMutation(entityType = "TASK", action = "STATUS_UPDATE")
    public TaskResponse updateStatus(UUID id, TaskStatusRequest request) {
        Task task = getTask(id);
        task.setStatus(request.status());
        return toResponse(task);
    }

    private Task getTask(UUID id) {
        return taskRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private void validateUser(UUID userId, UUID tenantId) {
        if (!userRepository.existsByIdAndTenantId(userId, tenantId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getUserId(), task.getTitle(), task.getDescription(), task.getDueAt(),
                task.getStatus(), task.isActive(), task.getCreatedAt(), task.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
