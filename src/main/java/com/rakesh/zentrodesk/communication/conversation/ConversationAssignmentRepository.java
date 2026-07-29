package com.rakesh.zentrodesk.communication.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConversationAssignmentRepository extends
        JpaRepository<ConversationAssignment, ConversationAssignmentId> {

    List<ConversationAssignment> findAllByConversationIdIn(Collection<UUID> conversationIds);

    List<ConversationAssignment> findAllByConversationIdAndTenantId(UUID conversationId, UUID tenantId);

    void deleteAllByConversationIdAndTenantId(UUID conversationId, UUID tenantId);
}
