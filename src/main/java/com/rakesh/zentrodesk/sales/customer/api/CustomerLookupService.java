package com.rakesh.zentrodesk.sales.customer.api;

import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerLookupService {

    private final CustomerRepository customerRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public List<CustomerLookupResponse> find(String query, int limit) {
        Specification<Customer> specification = Specification
                .where(SearchSpecificationBuilder.<Customer>textContains(query, Set.of("name", "city")))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", currentTenant.id()))
                .and(SearchSpecificationBuilder.booleanEquals("active", true));
        return customerRepository.findAll(
                        specification,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "name"))
                ).map(customer -> new CustomerLookupResponse(
                        customer.getId(),
                        customer.getName(),
                        customer.getCity()
                ))
                .getContent();
    }
}
