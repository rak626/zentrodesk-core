package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.communication.conversation.ConversationAssignment;
import com.rakesh.zentrodesk.communication.conversation.ConversationAssignmentRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationAssignmentRole;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageType;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversation;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationRepository;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfiguration;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfigurationRepository;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppFundingStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppMessageOutbox;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppOutboxAccess;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.User;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.customer.ContactNormalizer;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerConversationService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt", "updatedAt", "lastMessageAt", "status", "unreadCount"
    );
    private final CustomerConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ConversationAssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final WhatsAppConfigurationRepository configurationRepository;
    private final WhatsAppOutboxAccess outboxAccess;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<CustomerConversationResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            CustomerConversationStatus status,
            UUID assignedUserId
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<CustomerConversation> specification = Specification
                .where(SearchSpecificationBuilder.<CustomerConversation>uuidEquals("tenantId", tenantId))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status))
                .and(assignedTo(assignedUserId));
        Page<CustomerConversation> conversations = conversationRepository.findAll(specification, pageable);
        Map<UUID, List<ConversationAssignmentResponse>> assignments = assignments(
                conversations.getContent().stream().map(CustomerConversation::getId).toList()
        );
        return PageResponse.from(conversations.map(conversation ->
                toResponse(conversation, assignments.getOrDefault(conversation.getId(), List.of()))));
    }

    @Transactional
    @AuditMutation(entityType = "CUSTOMER_CONVERSATION", action = "CREATE")
    public CustomerConversationResponse create(CustomerConversationCreateRequest request) {
        UUID tenantId = currentTenant.id();
        Lead lead = request.leadId() == null
                ? null
                : leadRepository.findByIdAndTenantId(request.leadId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        UUID customerId = lead != null && lead.getCustomerId() != null
                ? lead.getCustomerId()
                : request.customerId();
        if (lead != null
                && lead.getCustomerId() != null
                && request.customerId() != null
                && !lead.getCustomerId().equals(request.customerId())) {
            throw new BadRequestException("Selected customer does not match the lead");
        }
        Customer customer = customerId == null
                ? null
                : customerRepository.findByIdAndTenantId(customerId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        String phone = customer != null
                ? customer.getPhone()
                : lead != null ? lead.getPhone() : request.phone();
        String normalizedPhone = ContactNormalizer.phone(phone);
        if (normalizedPhone == null) {
            throw new BadRequestException("A valid customer phone number is required");
        }
        CustomerConversation conversation = conversationRepository
                .findByTenantIdAndNormalizedPhone(tenantId, normalizedPhone)
                .orElseGet(() -> conversationRepository.save(CustomerConversation.builder()
                        .tenantId(tenantId)
                        .phone(phone.trim())
                        .normalizedPhone(normalizedPhone)
                        .status(CustomerConversationStatus.OPEN)
                        .active(true)
                        .build()));
        UUID previousOwnerId = primaryOwner(conversation);
        conversation.setCustomerId(customer == null ? null : customer.getId());
        conversation.setLeadId(lead == null ? null : lead.getId());
        conversation.setQuotationId(null);
        conversation.setBookingId(null);
        conversation.setDisplayName(customer != null
                ? customer.getName()
                : lead != null ? lead.getName() : trimToNull(request.displayName()));
        if (lead != null) {
            publishOwnership(
                    conversation,
                    previousOwnerId,
                    lead.getAssignedUserId(),
                    "CHAT_LINKED_TO_LEAD"
            );
        } else if (previousOwnerId != null) {
            publishOwnership(
                    conversation,
                    previousOwnerId,
                    null,
                    "CHAT_LINKED_TO_CUSTOMER"
            );
        }
        return toResponse(
                conversation,
                assignments(List.of(conversation.getId())).getOrDefault(conversation.getId(), List.of())
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> messages(UUID conversationId) {
        CustomerConversation conversation = getConversation(conversationId);
        return messageRepository.findAllByConversationIdAndTenantIdOrderByCreatedAtAsc(
                        conversation.getId(),
                        conversation.getTenantId()
                ).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    @AuditMutation(entityType = "CONVERSATION_MESSAGE", action = "SEND")
    public ConversationMessageResponse send(UUID conversationId, ConversationSendRequest request) {
        CustomerConversation conversation = getConversation(conversationId);
        WhatsAppConfiguration configuration = configurationRepository.findByTenantId(conversation.getTenantId())
                .filter(item -> item.getStatus() == WhatsAppConnectionStatus.CONNECTED)
                .filter(WhatsAppConfiguration::isTokenHealthy)
                .filter(item -> item.getFundingStatus() == WhatsAppFundingStatus.FUNDED)
                .filter(item -> item.getAccessTokenExpiresAt() == null
                        || item.getAccessTokenExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Tenant WhatsApp number is not connected"));
        if (!freeformAvailable(conversation, Instant.now())) {
            throw new BadRequestException("The customer service window is closed");
        }
        ConversationMessage message = messageRepository.save(ConversationMessage.builder()
                .tenantId(conversation.getTenantId())
                .conversationId(conversation.getId())
                .customerId(conversation.getCustomerId())
                .sentByUserId(currentTenant.userId())
                .businessPhoneNumberId(configuration.getPhoneNumberId())
                .phone(conversation.getPhone())
                .direction(ConversationMessageDirection.OUTBOUND)
                .message(request.message().trim())
                .messageType(ConversationMessageType.TEXT)
                .status(ConversationMessageStatus.PENDING)
                .active(true)
                .build());
        outboxAccess.enqueue(WhatsAppMessageOutbox.builder()
                .tenantId(configuration.getTenantId())
                .conversationId(conversation.getId())
                .messageId(message.getId())
                .senderPhoneNumberId(configuration.getPhoneNumberId())
                .nextAttemptAt(Instant.now())
                .build());
        conversation.setStatus(CustomerConversationStatus.OPEN);
        conversation.setLastMessageAt(Instant.now());
        return toMessageResponse(message);
    }

    @Transactional
    @AuditMutation(entityType = "CUSTOMER_CONVERSATION", action = "ASSIGN")
    public CustomerConversationResponse assign(UUID conversationId, ConversationAssignmentRequest request) {
        CustomerConversation conversation = getConversation(conversationId);
        if (conversation.getLeadId() == null
                && conversation.getQuotationId() == null
                && conversation.getBookingId() == null) {
            throw new BadRequestException("A customer-only conversation remains unassigned");
        }
        userRepository.findWithTenantRoleAndPermissionsByIdAndTenantId(
                    request.primaryAssigneeId(),
                    conversation.getTenantId()
                )
                .filter(User::isActive)
                .orElseThrow(() -> new BadRequestException("Assignee must be an active user in this tenant"));
        publishOwnership(
                conversation,
                primaryOwner(conversation),
                request.primaryAssigneeId(),
                "OWNER_REASSIGNED"
        );
        return toResponse(
                conversation,
                assignments(List.of(conversationId)).getOrDefault(conversationId, List.of())
        );
    }

    @Transactional
    public CustomerConversationResponse markRead(UUID conversationId) {
        CustomerConversation conversation = getConversation(conversationId);
        conversation.setUnreadCount(0);
        return toResponse(
                conversation,
                assignments(List.of(conversationId)).getOrDefault(conversationId, List.of())
        );
    }

    @Transactional
    @AuditMutation(entityType = "CUSTOMER_CONVERSATION", action = "STATUS_UPDATE")
    public CustomerConversationResponse updateState(UUID conversationId, ConversationStateRequest request) {
        CustomerConversation conversation = getConversation(conversationId);
        conversation.setStatus(request.status());
        return toResponse(
                conversation,
                assignments(List.of(conversationId)).getOrDefault(conversationId, List.of())
        );
    }

    public CustomerConversation findOrCreate(UUID tenantId, UUID customerId, String phone, String displayName) {
        String normalizedPhone = ContactNormalizer.phone(phone);
        if (normalizedPhone == null) {
            throw new BadRequestException("A valid phone number is required");
        }
        return conversationRepository.findByTenantIdAndNormalizedPhone(tenantId, normalizedPhone)
                .orElseGet(() -> conversationRepository.save(CustomerConversation.builder()
                        .tenantId(tenantId)
                        .customerId(customerId)
                        .phone(phone.trim())
                        .normalizedPhone(normalizedPhone)
                        .displayName(trimToNull(displayName))
                        .status(CustomerConversationStatus.OPEN)
                        .active(true)
                        .build()));
    }

    private Specification<CustomerConversation> assignedTo(UUID userId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.conjunction();
            }
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<ConversationAssignment> assignment = subquery.from(ConversationAssignment.class);
            subquery.select(assignment.get("conversationId"))
                    .where(
                            cb.equal(assignment.get("conversationId"), root.get("id")),
                            cb.equal(assignment.get("tenantId"), root.get("tenantId")),
                            cb.equal(assignment.get("userId"), userId)
                    );
            return cb.exists(subquery);
        };
    }

    private CustomerConversation getConversation(UUID id) {
        return conversationRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private Map<UUID, List<ConversationAssignmentResponse>> assignments(List<UUID> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        List<ConversationAssignment> assignments = assignmentRepository.findAllByConversationIdIn(conversationIds);
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(assignments.stream().map(ConversationAssignment::getUserId).toList())
                .forEach(user -> users.put(user.getId(), user));
        Map<UUID, List<ConversationAssignmentResponse>> result = new HashMap<>();
        for (ConversationAssignment assignment : assignments) {
            User user = users.get(assignment.getUserId());
            if (user == null) {
                continue;
            }
            result.computeIfAbsent(assignment.getConversationId(), ignored -> new ArrayList<>())
                    .add(toAssignmentResponse(assignment, user));
        }
        return result;
    }

    private List<ConversationAssignmentResponse> assignmentResponses(
            List<ConversationAssignment> assignments,
            List<User> users
    ) {
        Map<UUID, User> byId = new HashMap<>();
        users.forEach(user -> byId.put(user.getId(), user));
        return assignments.stream()
                .map(assignment -> toAssignmentResponse(assignment, byId.get(assignment.getUserId())))
                .toList();
    }

    private ConversationAssignmentResponse toAssignmentResponse(ConversationAssignment assignment, User user) {
        String userName = (user.getFirstName() + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new ConversationAssignmentResponse(user.getId(), userName, assignment.getRole());
    }

    private CustomerConversationResponse toResponse(
            CustomerConversation conversation,
            List<ConversationAssignmentResponse> assignments
    ) {
        Instant windowExpiresAt = conversation.getLastInboundAt() == null
                ? null
                : conversation.getLastInboundAt().plus(24, ChronoUnit.HOURS);
        return new CustomerConversationResponse(
                conversation.getId(),
                conversation.getCustomerId(),
                conversation.getLeadId(),
                conversation.getQuotationId(),
                conversation.getBookingId(),
                conversation.getPhone(),
                conversation.getDisplayName(),
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                conversation.getLastInboundAt(),
                windowExpiresAt,
                freeformAvailable(conversation, Instant.now()),
                conversation.getUnreadCount(),
                assignments,
                conversation.getVersion()
        );
    }

    private ConversationMessageResponse toMessageResponse(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getCustomerId(),
                message.getSentByUserId(),
                message.getPhone(),
                message.getDirection(),
                message.getMessage(),
                message.getMessageType(),
                message.getProviderMessageId(),
                message.getStatus(),
                message.isActive(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }

    private boolean freeformAvailable(CustomerConversation conversation, Instant now) {
        return conversation.getLastInboundAt() != null
                && conversation.getLastInboundAt().plus(24, ChronoUnit.HOURS).isAfter(now);
    }

    private UUID primaryOwner(CustomerConversation conversation) {
        return assignmentRepository.findAllByConversationIdAndTenantId(
                        conversation.getId(),
                        conversation.getTenantId()
                ).stream()
                .filter(assignment -> assignment.getRole() == ConversationAssignmentRole.PRIMARY)
                .map(ConversationAssignment::getUserId)
                .findFirst()
                .orElse(null);
    }

    private void publishOwnership(
            CustomerConversation conversation,
            UUID previousOwnerId,
            UUID ownerId,
            String reason
    ) {
        eventPublisher.publishEvent(new OwnershipChangedEvent(
                conversation.getTenantId(),
                previousOwnerId,
                ownerId,
                currentTenant.userId(),
                reason,
                "CONVERSATION",
                conversation.getId(),
                conversation.getLeadId(),
                conversation.getQuotationId(),
                conversation.getBookingId(),
                conversation.getId(),
                conversation.getCustomerId(),
                Instant.now()
        ));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
