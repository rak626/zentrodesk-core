package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationMessageService {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "phone", "status", "active");

    private final ConversationMessageRepository repository;
    private final CurrentTenant currentTenant;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public PageResponse<ConversationMessageResponse> findAll(int page, int size, String sort, String direction, String phone, UUID customerId, ConversationMessageStatus status) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<ConversationMessage> spec = Specification
                .where(SearchSpecificationBuilder.<ConversationMessage>uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("customerId", customerId))
                .and((root, query, cb) -> phone == null || phone.isBlank() ? cb.conjunction() : cb.equal(root.get("phone"), phone))
                .and((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        return PageResponse.from(repository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional
    @AuditMutation(entityType = "CONVERSATION_MESSAGE", action = "CREATE")
    public ConversationMessageResponse create(ConversationMessageRequest request) {
        UUID tenantId = currentTenant.id();
        if (request.customerId() != null) {
            customerRepository.findByIdAndTenantId(request.customerId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        }
        ConversationMessage message = ConversationMessage.builder()
                .tenantId(tenantId)
                .customerId(request.customerId())
                .phone(request.phone().trim())
                .direction(request.direction())
                .message(request.message().trim())
                .providerMessageId(trimToNull(request.providerMessageId()))
                .status(request.status() == null ? ConversationMessageStatus.PENDING : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(repository.save(message));
    }

    @Transactional
    @AuditMutation(entityType = "CONVERSATION_MESSAGE", action = "STATUS_UPDATE")
    public ConversationMessageResponse updateStatus(UUID id, ConversationStatusRequest request) {
        ConversationMessage message = getMessage(id);
        message.setStatus(request.status());
        return toResponse(message);
    }

    private ConversationMessage getMessage(UUID id) {
        return repository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation message not found"));
    }

    private ConversationMessageResponse toResponse(ConversationMessage message) {
        return new ConversationMessageResponse(message.getId(), message.getCustomerId(), message.getPhone(), message.getDirection(),
                message.getMessage(), message.getProviderMessageId(), message.getStatus(), message.isActive(),
                message.getCreatedAt(), message.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
