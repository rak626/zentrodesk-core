package com.rakesh.zentrodesk.sales.customer;

import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadActivity;
import com.rakesh.zentrodesk.sales.lead.LeadActivityRepository;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadCustomerConversionService {

    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final QuotationRepository quotationRepository;
    private final CurrentTenant currentTenant;

    @Transactional
    public Customer resolveForBooking(Quotation quotation) {
        if (quotation.getCustomerId() != null) {
            return customerRepository.findByIdAndTenantId(quotation.getCustomerId(), quotation.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        }
        if (quotation.getLeadId() == null) {
            throw new ConflictException("Quotation must have a lead or customer before conversion");
        }

        Lead lead = leadRepository.findByIdAndTenantId(quotation.getLeadId(), quotation.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        Customer customer = lead.getCustomerId() == null
                ? findOrCreateCustomer(lead)
                : customerRepository.findByIdAndTenantId(lead.getCustomerId(), lead.getTenantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Lead customer not found"));

        linkConversion(lead, quotation, customer);
        return customer;
    }

    private Customer findOrCreateCustomer(Lead lead) {
        String normalizedPhone = ContactNormalizer.phone(lead.getPhone());
        String normalizedEmail = ContactNormalizer.email(lead.getEmail());
        Customer phoneMatch = normalizedPhone == null
                ? null
                : customerRepository.findByTenantIdAndNormalizedPhoneAndActiveTrue(
                        lead.getTenantId(),
                        normalizedPhone
                ).orElse(null);
        List<Customer> emailMatches = normalizedEmail == null
                ? List.of()
                : customerRepository.findAllByTenantIdAndNormalizedEmailAndActiveTrue(
                        lead.getTenantId(),
                        normalizedEmail
                );
        if (emailMatches.size() > 1) {
            throw new ConflictException("Lead email matches multiple customers; link the customer manually");
        }
        Customer emailMatch = emailMatches.isEmpty() ? null : emailMatches.getFirst();
        if (phoneMatch != null && emailMatch != null && !phoneMatch.getId().equals(emailMatch.getId())) {
            throw new ConflictException("Lead phone and email match different customers; link the customer manually");
        }
        Customer matched = phoneMatch == null ? emailMatch : phoneMatch;
        if (matched != null) {
            return matched;
        }

        Customer customer = Customer.builder()
                .tenantId(lead.getTenantId())
                .name(lead.getName())
                .phone(lead.getPhone())
                .normalizedPhone(normalizedPhone)
                .email(lead.getEmail())
                .normalizedEmail(normalizedEmail)
                .notes("Created automatically when lead quotation was converted to booking.")
                .active(true)
                .build();
        try {
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Customer was created concurrently; retry booking conversion");
        }
    }

    private void linkConversion(Lead lead, Quotation quotation, Customer customer) {
        lead.setCustomerId(customer.getId());
        lead.setStatus(LeadStatus.WON);
        quotationRepository.findByTenantIdAndSeriesId(quotation.getTenantId(), quotation.getSeriesId())
                .forEach(revision -> revision.setCustomerId(customer.getId()));
        quotation.setCustomerId(customer.getId());
        leadActivityRepository.save(LeadActivity.builder()
                .tenantId(lead.getTenantId())
                .leadId(lead.getId())
                .actorId(currentTenant.userId())
                .activityType("CONVERTED")
                .summary("Lead converted to customer and booking")
                .build());
    }
}
