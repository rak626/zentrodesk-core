package com.rakesh.zentrodesk.sales.customer.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.customer.ContactNormalizer;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "updatedAt", "name", "city", "active");
    private static final CustomerRelationshipSummary EMPTY_RELATIONSHIPS =
            new CustomerRelationshipSummary(0, 0, 0);

    private final CustomerRepository customerRepository;
    private final CurrentTenant currentTenant;
    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Customer> specification = Specification
                .where(SearchSpecificationBuilder.<Customer>textContains(
                        search,
                        Set.of("name", "phone", "email", "address", "city", "state", "country")
                ))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active));
        Page<Customer> customers = customerRepository.findAll(specification, pageable);
        Map<UUID, CustomerRelationshipSummary> relationships = relationshipSummaries(
                tenantId,
                customers.getContent().stream().map(Customer::getId).toList()
        );
        return PageResponse.from(customers.map(customer -> toResponse(
                customer,
                relationships.getOrDefault(customer.getId(), EMPTY_RELATIONSHIPS)
        )));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        return toResponse(getCustomer(id));
    }

    @Transactional(readOnly = true)
    public CustomerDuplicateResponse duplicates(String phone, String email) {
        UUID tenantId = currentTenant.id();
        String normalizedPhone = ContactNormalizer.phone(phone);
        String normalizedEmail = ContactNormalizer.email(email);
        boolean duplicatePhone = normalizedPhone != null
                && customerRepository.existsByTenantIdAndNormalizedPhoneAndActiveTrue(tenantId, normalizedPhone);
        boolean duplicateEmail = normalizedEmail != null && jdbcClient.sql("""
                        select exists(
                            select 1 from zentro_desk.customers
                            where tenant_id = :tenantId
                              and normalized_email = :email
                              and active = true
                        )
                        """)
                .param("tenantId", tenantId)
                .param("email", normalizedEmail)
                .query(Boolean.class)
                .single();
        return new CustomerDuplicateResponse(duplicatePhone, duplicateEmail);
    }

    @Transactional
    @AuditMutation(entityType = "CUSTOMER", action = "CREATE")
    public CustomerResponse create(CustomerRequest request) {
        UUID tenantId = currentTenant.id();
        String normalizedPhone = ContactNormalizer.phone(request.phone());
        rejectDuplicatePhone(tenantId, normalizedPhone, null);
        Customer customer = Customer.builder()
                .tenantId(tenantId)
                .name(request.name().trim())
                .phone(trimToNull(request.phone()))
                .normalizedPhone(normalizedPhone)
                .email(trimToNull(request.email()))
                .normalizedEmail(ContactNormalizer.email(request.email()))
                .address(trimToNull(request.address()))
                .city(trimToNull(request.city()))
                .state(trimToNull(request.state()))
                .country(trimToNull(request.country()))
                .notes(trimToNull(request.notes()))
                .active(request.active() == null || request.active())
                .build();
        try {
            return toResponse(customerRepository.saveAndFlush(customer));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("An active customer with this phone already exists");
        }
    }

    @Transactional
    @AuditMutation(entityType = "CUSTOMER", action = "UPDATE")
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        assertVersion(customer.getVersion(), request.version());
        String normalizedPhone = ContactNormalizer.phone(request.phone());
        rejectDuplicatePhone(customer.getTenantId(), normalizedPhone, id);
        customer.setName(request.name().trim());
        customer.setPhone(trimToNull(request.phone()));
        customer.setNormalizedPhone(normalizedPhone);
        customer.setEmail(trimToNull(request.email()));
        customer.setNormalizedEmail(ContactNormalizer.email(request.email()));
        customer.setAddress(trimToNull(request.address()));
        customer.setCity(trimToNull(request.city()));
        customer.setState(trimToNull(request.state()));
        customer.setCountry(trimToNull(request.country()));
        customer.setNotes(trimToNull(request.notes()));
        if (request.active() != null) {
            customer.setActive(request.active());
        }
        try {
            return toResponse(customerRepository.saveAndFlush(customer));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("An active customer with this phone already exists");
        }
    }

    @Transactional
    @AuditMutation(entityType = "CUSTOMER", action = "DISABLE")
    public void disable(UUID id) {
        getCustomer(id).setActive(false);
    }

    private Customer getCustomer(UUID id) {
        return customerRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    private void rejectDuplicatePhone(UUID tenantId, String normalizedPhone, UUID excludedId) {
        if (normalizedPhone == null) {
            return;
        }
        boolean duplicate = excludedId == null
                ? customerRepository.existsByTenantIdAndNormalizedPhoneAndActiveTrue(tenantId, normalizedPhone)
                : customerRepository.existsByTenantIdAndNormalizedPhoneAndActiveTrueAndIdNot(
                        tenantId,
                        normalizedPhone,
                        excludedId
                );
        if (duplicate) {
            throw new ConflictException("An active customer with this phone already exists");
        }
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Customer was updated by another user");
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        CustomerRelationshipSummary relationships = relationshipSummaries(
                customer.getTenantId(),
                List.of(customer.getId())
        ).getOrDefault(customer.getId(), EMPTY_RELATIONSHIPS);
        return toResponse(customer, relationships);
    }

    private Map<UUID, CustomerRelationshipSummary> relationshipSummaries(
            UUID tenantId,
            Collection<UUID> customerIds
    ) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        List<Map.Entry<UUID, CustomerRelationshipSummary>> entries = jdbcClient.sql("""
                        select relationship.customer_id,
                               sum(relationship.leads) as leads,
                               sum(relationship.quotations) as quotations,
                               sum(relationship.bookings) as bookings
                        from (
                            select customer_id, count(*) as leads, 0 as quotations, 0 as bookings
                            from zentro_desk.leads
                            where tenant_id = :tenantId and customer_id in (:customerIds)
                            group by customer_id
                            union all
                            select customer_id, 0 as leads, count(*) as quotations, 0 as bookings
                            from zentro_desk.quotations
                            where tenant_id = :tenantId and customer_id in (:customerIds)
                            group by customer_id
                            union all
                            select customer_id, 0 as leads, 0 as quotations, count(*) as bookings
                            from zentro_desk.bookings
                            where tenant_id = :tenantId and customer_id in (:customerIds)
                            group by customer_id
                        ) relationship
                        group by relationship.customer_id
                        """)
                .param("tenantId", tenantId)
                .param("customerIds", customerIds)
                .query((resultSet, rowNumber) -> Map.entry(
                        resultSet.getObject("customer_id", UUID.class),
                        new CustomerRelationshipSummary(
                                resultSet.getLong("leads"),
                                resultSet.getLong("quotations"),
                                resultSet.getLong("bookings")
                        )
                ))
                .list();
        Map<UUID, CustomerRelationshipSummary> relationships = new HashMap<>();
        entries.forEach(entry -> relationships.put(entry.getKey(), entry.getValue()));
        return relationships;
    }

    private CustomerResponse toResponse(
            Customer customer,
            CustomerRelationshipSummary relationships
    ) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getCity(),
                customer.getState(),
                customer.getCountry(),
                customer.getNotes(),
                customer.isActive(),
                customer.getVersion(),
                relationships,
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
