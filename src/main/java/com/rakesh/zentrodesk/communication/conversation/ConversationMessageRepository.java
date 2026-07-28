package com.rakesh.zentrodesk.communication.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID>, JpaSpecificationExecutor<ConversationMessage> {

    Optional<ConversationMessage> findByIdAndTenantId(UUID id, UUID tenantId);
}
