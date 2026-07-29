package com.rakesh.zentrodesk.communication.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CustomerConversationRepository extends
        JpaRepository<CustomerConversation, UUID>,
        JpaSpecificationExecutor<CustomerConversation> {

    Optional<CustomerConversation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CustomerConversation> findByTenantIdAndNormalizedPhone(UUID tenantId, String normalizedPhone);
}
