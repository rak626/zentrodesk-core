package com.rakesh.zentrodesk.sales.customer;

import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesReferenceService {

    private final CustomerRepository customerRepository;
    private final QuotationRepository quotationRepository;

    @Transactional(readOnly = true)
    public void requireCustomer(UUID customerId, UUID tenantId) {
        customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    @Transactional(readOnly = true)
    public void requireQuotation(UUID quotationId, UUID tenantId) {
        if (quotationId == null) {
            return;
        }
        quotationRepository.findByIdAndTenantId(quotationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
    }
}
