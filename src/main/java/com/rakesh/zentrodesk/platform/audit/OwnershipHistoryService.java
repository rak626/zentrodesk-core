package com.rakesh.zentrodesk.platform.audit;

import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class OwnershipHistoryService {

    private final OwnershipHistoryRepository repository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    List<OwnershipHistoryResponse> findAll(
            UUID leadId,
            UUID quotationId,
            UUID bookingId,
            UUID conversationId
    ) {
        Specification<OwnershipHistory> specification = Specification
                .where(SearchSpecificationBuilder.<OwnershipHistory>uuidEquals("tenantId", currentTenant.id()))
                .and(SearchSpecificationBuilder.uuidEquals("leadId", leadId))
                .and(SearchSpecificationBuilder.uuidEquals("quotationId", quotationId))
                .and(SearchSpecificationBuilder.uuidEquals("bookingId", bookingId))
                .and(SearchSpecificationBuilder.uuidEquals("conversationId", conversationId));
        return repository.findAll(
                        specification,
                        PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "occurredAt"))
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    private OwnershipHistoryResponse toResponse(OwnershipHistory history) {
        return new OwnershipHistoryResponse(
                history.getId(),
                history.getPreviousOwnerId(),
                history.getOwnerId(),
                history.getActorId(),
                history.getReason(),
                history.getSourceType(),
                history.getSourceId(),
                history.getLeadId(),
                history.getQuotationId(),
                history.getBookingId(),
                history.getConversationId(),
                history.getCustomerId(),
                history.getOccurredAt()
        );
    }
}
