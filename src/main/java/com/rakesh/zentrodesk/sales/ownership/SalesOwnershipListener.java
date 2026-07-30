package com.rakesh.zentrodesk.sales.ownership;

import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadActivity;
import com.rakesh.zentrodesk.sales.lead.LeadActivityRepository;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SalesOwnershipListener {

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final QuotationRepository quotationRepository;

    @EventListener
    void on(OwnershipChangedEvent event) {
        if (event.leadId() != null) {
            leadRepository.findByIdAndTenantId(event.leadId(), event.tenantId()).ifPresent(lead -> {
                lead.setAssignedUserId(event.ownerId());
                recordLeadActivity(lead, event);
            });
        }

        Map<UUID, Quotation> quotations = new LinkedHashMap<>();
        if (event.leadId() != null) {
            quotationRepository.findAllByTenantIdAndLeadId(event.tenantId(), event.leadId())
                    .forEach(quotation -> quotations.put(quotation.getId(), quotation));
        }
        if (event.quotationId() != null) {
            quotationRepository.findByIdAndTenantId(event.quotationId(), event.tenantId())
                    .ifPresent(quotation -> {
                        quotationRepository.findByTenantIdAndSeriesId(
                                        event.tenantId(),
                                        quotation.getSeriesId()
                                )
                                .forEach(revision -> quotations.put(revision.getId(), revision));
                    });
        }
        quotations.values().forEach(quotation -> quotation.setAssignedUserId(event.ownerId()));
    }

    private void recordLeadActivity(Lead lead, OwnershipChangedEvent event) {
        leadActivityRepository.save(LeadActivity.builder()
                .tenantId(event.tenantId())
                .leadId(lead.getId())
                .actorId(event.actorId())
                .activityType("OWNER_CHANGED")
                .summary("Ownership changed: " + event.reason())
                .build());
    }
}
