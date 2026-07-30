package com.rakesh.zentrodesk.platform.audit;

import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OwnershipHistoryListener {

    private final OwnershipHistoryRepository repository;

    @EventListener
    void on(OwnershipChangedEvent event) {
        repository.save(OwnershipHistory.builder()
                .tenantId(event.tenantId())
                .previousOwnerId(event.previousOwnerId())
                .ownerId(event.ownerId())
                .actorId(event.actorId())
                .reason(event.reason())
                .sourceType(event.sourceType())
                .sourceId(event.sourceId())
                .leadId(event.leadId())
                .quotationId(event.quotationId())
                .bookingId(event.bookingId())
                .conversationId(event.conversationId())
                .customerId(event.customerId())
                .occurredAt(event.occurredAt())
                .build());
    }
}
