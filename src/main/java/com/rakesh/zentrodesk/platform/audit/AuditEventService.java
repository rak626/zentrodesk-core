package com.rakesh.zentrodesk.platform.audit;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
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
public class AuditEventService {

    private static final Set<String> ALLOWED_SORTS = Set.of("occurredAt", "entityType", "action");

    private final AuditEventRepository auditEventRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String entityType,
            UUID entityId,
            String action,
            Instant from,
            Instant to
    ) {
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<AuditEvent> specification = Specification
                .where(SearchSpecificationBuilder.<AuditEvent>uuidEquals("tenantId", currentTenant.id()))
                .and(textEquals("entityType", entityType))
                .and(SearchSpecificationBuilder.uuidEquals("entityId", entityId))
                .and(textEquals("action", action))
                .and(occurredBetween(from, to));
        return PageResponse.from(auditEventRepository.findAll(specification, pageable).map(this::toResponse));
    }

    public void record(
            UUID tenantId,
            UUID actorId,
            String entityType,
            UUID entityId,
            String action,
            String correlationId,
            String beforeSummary,
            String afterSummary
    ) {
        auditEventRepository.save(AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .occurredAt(Instant.now())
                .correlationId(correlationId)
                .beforeSummary(beforeSummary)
                .afterSummary(afterSummary)
                .build());
    }

    private Specification<AuditEvent> textEquals(String field, String value) {
        return (root, query, cb) -> value == null || value.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get(field), value.trim().toUpperCase());
    }

    private Specification<AuditEvent> occurredBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("occurredAt"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
            }
            if (to != null) {
                return cb.lessThanOrEqualTo(root.get("occurredAt"), to);
            }
            return cb.conjunction();
        };
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getActorId(), event.getEntityType(), event.getEntityId(),
                event.getAction(), event.getOccurredAt(), event.getCorrelationId(), event.getBeforeSummary(),
                event.getAfterSummary());
    }
}
