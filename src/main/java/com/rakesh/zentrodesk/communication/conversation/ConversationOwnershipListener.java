package com.rakesh.zentrodesk.communication.conversation;

import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ConversationOwnershipListener {

    private final CustomerConversationRepository conversationRepository;
    private final ConversationAssignmentRepository assignmentRepository;

    @EventListener
    void on(OwnershipChangedEvent event) {
        Map<UUID, CustomerConversation> conversations = new LinkedHashMap<>();
        if (event.leadId() != null) {
            conversationRepository.findAllByTenantIdAndLeadId(event.tenantId(), event.leadId())
                    .forEach(conversation -> conversations.put(conversation.getId(), conversation));
        }
        if (event.quotationId() != null) {
            conversationRepository.findAllByTenantIdAndQuotationId(event.tenantId(), event.quotationId())
                    .forEach(conversation -> conversations.put(conversation.getId(), conversation));
        }
        if (event.bookingId() != null) {
            conversationRepository.findAllByTenantIdAndBookingId(event.tenantId(), event.bookingId())
                    .forEach(conversation -> conversations.put(conversation.getId(), conversation));
        }
        if (event.conversationId() != null) {
            conversationRepository.findByIdAndTenantId(event.conversationId(), event.tenantId())
                    .ifPresent(conversation -> conversations.put(conversation.getId(), conversation));
        }
        conversations.values().forEach(conversation -> replaceOwner(conversation, event));
    }

    private void replaceOwner(CustomerConversation conversation, OwnershipChangedEvent event) {
        assignmentRepository.deleteAllByConversationIdAndTenantId(conversation.getId(), event.tenantId());
        assignmentRepository.flush();
        if (event.ownerId() == null) {
            return;
        }
        assignmentRepository.save(ConversationAssignment.builder()
                .conversationId(conversation.getId())
                .tenantId(event.tenantId())
                .userId(event.ownerId())
                .role(ConversationAssignmentRole.PRIMARY)
                .assignedByUserId(event.actorId())
                .assignedAt(Instant.now())
                .build());
    }
}
