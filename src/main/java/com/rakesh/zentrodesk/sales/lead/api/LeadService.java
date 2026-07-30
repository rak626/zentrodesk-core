package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadActivity;
import com.rakesh.zentrodesk.sales.lead.LeadActivityRepository;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import com.rakesh.zentrodesk.sales.customer.ContactNormalizer;
import com.rakesh.zentrodesk.sales.customer.SalesReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "travelDate", "status", "active");
    private static final Map<LeadStatus, Set<LeadStatus>> TRANSITIONS = Map.of(
            LeadStatus.NEW, Set.of(LeadStatus.CONTACTED, LeadStatus.CANCELLED, LeadStatus.LOST),
            LeadStatus.CONTACTED, Set.of(LeadStatus.QUALIFIED, LeadStatus.LOST, LeadStatus.CANCELLED),
            LeadStatus.QUALIFIED, Set.of(LeadStatus.QUOTATION_SENT, LeadStatus.LOST, LeadStatus.CANCELLED),
            LeadStatus.QUOTATION_SENT, Set.of(LeadStatus.NEGOTIATING, LeadStatus.WON, LeadStatus.LOST, LeadStatus.CANCELLED),
            LeadStatus.NEGOTIATING, Set.of(LeadStatus.WON, LeadStatus.LOST, LeadStatus.CANCELLED),
            LeadStatus.WON, Set.of(),
            LeadStatus.LOST, Set.of(),
            LeadStatus.CANCELLED, Set.of()
    );

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final UserRepository userRepository;
    private final SalesReferenceService salesReferenceService;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> findAll(int page, int size, String sort, String direction, String search,
                                              LeadStatus status, UUID assignedUserId, Boolean active) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Lead> specification = Specification
                .where(SearchSpecificationBuilder.<Lead>textContains(search, Set.of("name", "phone", "email", "source", "destination", "remarks")))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("assignedUserId", assignedUserId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        return PageResponse.from(leadRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public LeadResponse findById(UUID id) {
        return toResponse(getLead(id));
    }

    @Transactional(readOnly = true)
    public Map<LeadStatus, Long> pipeline() {
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            counts.put(status, 0L);
        }
        leadRepository.countPipeline(currentTenant.id())
                .forEach(row -> counts.put((LeadStatus) row[0], (Long) row[1]));
        return counts;
    }

    @Transactional(readOnly = true)
    public LeadDuplicateResponse duplicates(String phone) {
        String normalized = ContactNormalizer.phone(phone);
        return new LeadDuplicateResponse(normalized != null
                && leadRepository.existsByTenantIdAndNormalizedPhoneAndActiveTrue(currentTenant.id(), normalized));
    }

    @Transactional
    @AuditMutation(entityType = "LEAD", action = "CREATE")
    public LeadResponse create(LeadRequest request) {
        UUID tenantId = currentTenant.id();
        validateCustomer(request.customerId(), tenantId);
        UUID ownerId = currentTenant.userId();
        Lead lead = Lead.builder()
                .tenantId(tenantId)
                .assignedUserId(ownerId)
                .customerId(request.customerId())
                .name(request.name().trim())
                .phone(trimToNull(request.phone()))
                .normalizedPhone(ContactNormalizer.phone(request.phone()))
                .email(trimToNull(request.email()))
                .source(trimToNull(request.source()))
                .destination(trimToNull(request.destination()))
                .travelDate(request.travelDate())
                .adults(request.adults())
                .children(request.children())
                .budget(request.budget())
                .status(request.status() == null ? LeadStatus.NEW : request.status())
                .remarks(trimToNull(request.remarks()))
                .active(request.active() == null || request.active())
                .build();
        Lead saved = leadRepository.save(lead);
        recordActivity(saved, "CREATED", "Lead created");
        publishOwnership(saved, null, ownerId, "LEAD_CREATED");
        return toResponse(saved);
    }

    @Transactional
    @AuditMutation(entityType = "LEAD", action = "UPDATE")
    public LeadResponse update(UUID id, LeadRequest request) {
        Lead lead = getLead(id);
        assertVersion(lead.getVersion(), request.version());
        validateCustomer(request.customerId(), lead.getTenantId());
        lead.setCustomerId(request.customerId());
        lead.setName(request.name().trim());
        lead.setPhone(trimToNull(request.phone()));
        lead.setNormalizedPhone(ContactNormalizer.phone(request.phone()));
        lead.setEmail(trimToNull(request.email()));
        lead.setSource(trimToNull(request.source()));
        lead.setDestination(trimToNull(request.destination()));
        lead.setTravelDate(request.travelDate());
        lead.setAdults(request.adults());
        lead.setChildren(request.children());
        lead.setBudget(request.budget());
        if (request.status() != null && request.status() != lead.getStatus()) {
            validateTransition(lead.getStatus(), request.status());
            lead.setStatus(request.status());
            recordActivity(lead, "STATUS_CHANGED", "Lead status changed to " + request.status());
        }
        lead.setRemarks(trimToNull(request.remarks()));
        if (request.active() != null) {
            lead.setActive(request.active());
        }
        return toResponse(lead);
    }

    @Transactional
    @AuditMutation(entityType = "LEAD", action = "STATUS_UPDATE")
    public LeadResponse updateStatus(UUID id, LeadStatusRequest request) {
        Lead lead = getLead(id);
        assertVersion(lead.getVersion(), request.version());
        if (request.status() == lead.getStatus()) {
            return toResponse(lead);
        }
        validateTransition(lead.getStatus(), request.status());
        lead.setStatus(request.status());
        recordActivity(lead, "STATUS_CHANGED", "Lead status changed to " + request.status());
        return toResponse(lead);
    }

    @Transactional
    @AuditMutation(entityType = "LEAD", action = "ASSIGN")
    public LeadResponse assign(UUID id, LeadAssignRequest request) {
        Lead lead = getLead(id);
        assertVersion(lead.getVersion(), request.version());
        validateAssignedUser(request.assignedUserId(), lead.getTenantId());
        UUID previousOwnerId = lead.getAssignedUserId();
        publishOwnership(lead, previousOwnerId, request.assignedUserId(), "OWNER_REASSIGNED");
        return toResponse(lead);
    }

    @Transactional
    @AuditMutation(entityType = "LEAD", action = "DISABLE")
    public void delete(UUID id) {
        getLead(id).setActive(false);
    }

    private Lead getLead(UUID id) {
        return leadRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
    }

    private void validateAssignedUser(UUID userId, UUID tenantId) {
        if (userId == null) {
            return;
        }
        if (!userRepository.existsByIdAndTenantId(userId, tenantId)) {
            throw new ResourceNotFoundException("Assigned user not found");
        }
    }

    private void validateCustomer(UUID customerId, UUID tenantId) {
        if (customerId != null) {
            salesReferenceService.requireCustomer(customerId, tenantId);
        }
    }

    private void validateTransition(LeadStatus current, LeadStatus target) {
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BadRequestException("Lead cannot transition from " + current + " to " + target);
        }
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Lead was updated by another user");
        }
    }

    private void recordActivity(Lead lead, String type, String summary) {
        leadActivityRepository.save(LeadActivity.builder()
                .tenantId(lead.getTenantId())
                .leadId(lead.getId())
                .actorId(currentTenant.userId())
                .activityType(type)
                .summary(summary)
                .build());
    }

    private void publishOwnership(Lead lead, UUID previousOwnerId, UUID ownerId, String reason) {
        eventPublisher.publishEvent(new OwnershipChangedEvent(
                lead.getTenantId(),
                previousOwnerId,
                ownerId,
                currentTenant.userId(),
                reason,
                "LEAD",
                lead.getId(),
                lead.getId(),
                null,
                null,
                null,
                lead.getCustomerId(),
                Instant.now()
        ));
    }

    private LeadResponse toResponse(Lead lead) {
        return new LeadResponse(lead.getId(), lead.getAssignedUserId(), lead.getName(), lead.getPhone(), lead.getEmail(),
                lead.getSource(), lead.getDestination(), lead.getTravelDate(), lead.getAdults(), lead.getChildren(),
                lead.getBudget(), lead.getStatus(), lead.getRemarks(), lead.isActive(), lead.getCustomerId(),
                lead.getVersion(), lead.getCreatedAt(), lead.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
