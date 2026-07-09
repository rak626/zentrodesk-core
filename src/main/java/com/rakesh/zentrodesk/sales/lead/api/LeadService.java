package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "name", "travelDate", "status", "active");

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CurrentTenant currentTenant;

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

    @Transactional
    public LeadResponse create(LeadRequest request) {
        UUID tenantId = currentTenant.id();
        validateAssignedUser(request.assignedUserId(), tenantId);
        Lead lead = Lead.builder()
                .tenantId(tenantId)
                .assignedUserId(request.assignedUserId())
                .name(request.name().trim())
                .phone(trimToNull(request.phone()))
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
        return toResponse(leadRepository.save(lead));
    }

    @Transactional
    public LeadResponse update(UUID id, LeadRequest request) {
        Lead lead = getLead(id);
        validateAssignedUser(request.assignedUserId(), lead.getTenantId());
        lead.setAssignedUserId(request.assignedUserId());
        lead.setName(request.name().trim());
        lead.setPhone(trimToNull(request.phone()));
        lead.setEmail(trimToNull(request.email()));
        lead.setSource(trimToNull(request.source()));
        lead.setDestination(trimToNull(request.destination()));
        lead.setTravelDate(request.travelDate());
        lead.setAdults(request.adults());
        lead.setChildren(request.children());
        lead.setBudget(request.budget());
        if (request.status() != null) {
            lead.setStatus(request.status());
        }
        lead.setRemarks(trimToNull(request.remarks()));
        if (request.active() != null) {
            lead.setActive(request.active());
        }
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse updateStatus(UUID id, LeadStatusRequest request) {
        Lead lead = getLead(id);
        lead.setStatus(request.status());
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse assign(UUID id, LeadAssignRequest request) {
        Lead lead = getLead(id);
        validateAssignedUser(request.assignedUserId(), lead.getTenantId());
        lead.setAssignedUserId(request.assignedUserId());
        return toResponse(lead);
    }

    @Transactional
    public void delete(UUID id) {
        getLead(id).setActive(false);
    }

    private Lead getLead(UUID id) {
        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        currentTenant.require(lead.getTenantId());
        return lead;
    }

    private void validateAssignedUser(UUID userId, UUID tenantId) {
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Assigned user not found"));
        currentTenant.require(user.getTenant().getId());
        currentTenant.require(tenantId);
    }

    private LeadResponse toResponse(Lead lead) {
        return new LeadResponse(lead.getId(), lead.getAssignedUserId(), lead.getName(), lead.getPhone(), lead.getEmail(),
                lead.getSource(), lead.getDestination(), lead.getTravelDate(), lead.getAdults(), lead.getChildren(),
                lead.getBudget(), lead.getStatus(), lead.getRemarks(), lead.isActive(), lead.getCreatedAt(), lead.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
