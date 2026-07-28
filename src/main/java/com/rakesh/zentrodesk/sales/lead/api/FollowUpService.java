package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.lead.FollowUpPriority;
import com.rakesh.zentrodesk.sales.lead.FollowUpScheduledEvent;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadActivity;
import com.rakesh.zentrodesk.sales.lead.LeadActivityRepository;
import com.rakesh.zentrodesk.sales.lead.LeadFollowUp;
import com.rakesh.zentrodesk.sales.lead.LeadFollowUpRepository;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.lead.ReminderState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowUpService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "updatedAt", "dueAt", "priority", "completedAt");

    private final LeadFollowUpRepository followUpRepository;
    private final LeadActivityRepository activityRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<FollowUpResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            UUID leadId,
            UUID ownerId,
            Boolean completed,
            Boolean overdue
    ) {
        UUID tenantId = currentTenant.id();
        if (leadId != null) {
            requireLead(leadId, tenantId);
        }
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Instant now = Instant.now();
        Specification<LeadFollowUp> specification = Specification
                .where(SearchSpecificationBuilder.<LeadFollowUp>uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("leadId", leadId))
                .and(SearchSpecificationBuilder.uuidEquals("ownerId", ownerId))
                .and((root, query, cb) -> completed == null
                        ? cb.conjunction()
                        : completed
                                ? cb.isNotNull(root.get("completedAt"))
                                : cb.isNull(root.get("completedAt")))
                .and((root, query, cb) -> overdue == null || !overdue
                        ? cb.conjunction()
                        : cb.and(cb.isNull(root.get("completedAt")), cb.lessThan(root.get("dueAt"), now)));
        return PageResponse.from(followUpRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public FollowUpResponse findById(UUID id) {
        return toResponse(getFollowUp(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadActivityResponse> timeline(UUID leadId, int page, int size) {
        UUID tenantId = currentTenant.id();
        requireLead(leadId, tenantId);
        Pageable pageable = PaginationUtils.pageable(
                page,
                size,
                "createdAt",
                "DESC",
                Set.of("createdAt")
        );
        return PageResponse.from(activityRepository.findByTenantIdAndLeadId(tenantId, leadId, pageable)
                .map(activity -> new LeadActivityResponse(
                        activity.getId(),
                        activity.getActorId(),
                        activity.getActivityType(),
                        activity.getSummary(),
                        activity.getCreatedAt()
                )));
    }

    @Transactional
    @AuditMutation(entityType = "FOLLOW_UP", action = "CREATE")
    public FollowUpResponse create(FollowUpRequest request) {
        UUID tenantId = currentTenant.id();
        Lead lead = requireLead(request.leadId(), tenantId);
        requireOwner(request.ownerId(), tenantId);
        LeadFollowUp followUp = followUpRepository.save(LeadFollowUp.builder()
                .tenantId(tenantId)
                .leadId(lead.getId())
                .ownerId(request.ownerId())
                .dueAt(request.dueAt())
                .priority(request.priority() == null ? FollowUpPriority.NORMAL : request.priority())
                .notes(trimToNull(request.notes()))
                .reminderState(request.reminderState() == null ? ReminderState.PENDING : request.reminderState())
                .active(request.active() == null || request.active())
                .build());
        recordActivity(lead.getId(), "FOLLOW_UP_CREATED", "Follow-up scheduled");
        eventPublisher.publishEvent(new FollowUpScheduledEvent(
                tenantId,
                followUp.getId(),
                lead.getId(),
                followUp.getOwnerId(),
                followUp.getDueAt(),
                followUp.getNotes()
        ));
        return toResponse(followUp);
    }

    @Transactional
    @AuditMutation(entityType = "FOLLOW_UP", action = "UPDATE")
    public FollowUpResponse update(UUID id, FollowUpRequest request) {
        LeadFollowUp followUp = getFollowUp(id);
        assertVersion(followUp.getVersion(), request.version());
        requireLead(request.leadId(), followUp.getTenantId());
        requireOwner(request.ownerId(), followUp.getTenantId());
        followUp.setLeadId(request.leadId());
        followUp.setOwnerId(request.ownerId());
        followUp.setDueAt(request.dueAt());
        followUp.setPriority(request.priority() == null ? FollowUpPriority.NORMAL : request.priority());
        followUp.setNotes(trimToNull(request.notes()));
        if (request.reminderState() != null) {
            followUp.setReminderState(request.reminderState());
        }
        if (request.active() != null) {
            followUp.setActive(request.active());
        }
        recordActivity(followUp.getLeadId(), "FOLLOW_UP_UPDATED", "Follow-up updated");
        return toResponse(followUp);
    }

    @Transactional
    @AuditMutation(entityType = "FOLLOW_UP", action = "COMPLETE")
    public FollowUpResponse complete(UUID id, FollowUpCompletionRequest request) {
        LeadFollowUp followUp = getFollowUp(id);
        if (followUp.getCompletedAt() != null) {
            return toResponse(followUp);
        }
        assertVersion(followUp.getVersion(), request.version());
        followUp.setOutcome(request.outcome().trim());
        followUp.setCompletedAt(Instant.now());
        followUp.setReminderState(ReminderState.DISABLED);
        recordActivity(followUp.getLeadId(), "FOLLOW_UP_COMPLETED", "Follow-up completed");
        return toResponse(followUp);
    }

    @Transactional
    @AuditMutation(entityType = "FOLLOW_UP", action = "DISABLE")
    public void disable(UUID id) {
        LeadFollowUp followUp = getFollowUp(id);
        followUp.setActive(false);
        followUp.setReminderState(ReminderState.DISABLED);
        recordActivity(followUp.getLeadId(), "FOLLOW_UP_DISABLED", "Follow-up disabled");
    }

    private LeadFollowUp getFollowUp(UUID id) {
        return followUpRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Follow-up not found"));
    }

    private Lead requireLead(UUID leadId, UUID tenantId) {
        return leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
    }

    private void requireOwner(UUID ownerId, UUID tenantId) {
        if (!userRepository.existsByIdAndTenantId(ownerId, tenantId)) {
            throw new ResourceNotFoundException("Follow-up owner not found");
        }
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Follow-up was updated by another user");
        }
    }

    private void recordActivity(UUID leadId, String type, String summary) {
        activityRepository.save(LeadActivity.builder()
                .tenantId(currentTenant.id())
                .leadId(leadId)
                .actorId(currentTenant.userId())
                .activityType(type)
                .summary(summary)
                .build());
    }

    private FollowUpResponse toResponse(LeadFollowUp followUp) {
        boolean overdue = followUp.isActive()
                && followUp.getCompletedAt() == null
                && followUp.getDueAt().isBefore(Instant.now());
        return new FollowUpResponse(
                followUp.getId(),
                followUp.getLeadId(),
                followUp.getOwnerId(),
                followUp.getDueAt(),
                followUp.getPriority(),
                followUp.getNotes(),
                followUp.getOutcome(),
                followUp.getCompletedAt(),
                followUp.getReminderState(),
                overdue,
                followUp.isActive(),
                followUp.getVersion(),
                followUp.getCreatedAt(),
                followUp.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
