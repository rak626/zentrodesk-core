package com.rakesh.zentrodesk.communication.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CustomerConversationRepository extends
        JpaRepository<CustomerConversation, UUID>,
        JpaSpecificationExecutor<CustomerConversation> {

    Optional<CustomerConversation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CustomerConversation> findByTenantIdAndNormalizedPhone(UUID tenantId, String normalizedPhone);

    List<CustomerConversation> findAllByTenantIdAndLeadId(UUID tenantId, UUID leadId);

    List<CustomerConversation> findAllByTenantIdAndQuotationId(UUID tenantId, UUID quotationId);

    List<CustomerConversation> findAllByTenantIdAndBookingId(UUID tenantId, UUID bookingId);
}
