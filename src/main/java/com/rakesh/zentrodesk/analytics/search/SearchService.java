package com.rakesh.zentrodesk.analytics.search;

import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.customer.Customer;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final CurrentTenant currentTenant;
    private final SearchRateLimiter rateLimiter;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public SearchResponse search(
            String query,
            Set<SearchGroup> requestedGroups,
            int limit,
            Collection<? extends GrantedAuthority> authorities
    ) {
        UUID tenantId = currentTenant.id();
        rateLimiter.requireAllowed(currentTenant.userId());
        Set<String> permissions = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<SearchGroup> groups = requestedGroups == null || requestedGroups.isEmpty()
                ? EnumSet.allOf(SearchGroup.class)
                : EnumSet.copyOf(requestedGroups);
        String term = query.trim().toLowerCase(Locale.ROOT);
        List<SearchGroupResponse> results = new ArrayList<>();

        if (groups.contains(SearchGroup.CUSTOMERS) && permissions.contains(TenantPermissions.CUSTOMER_READ)) {
            results.add(new SearchGroupResponse(SearchGroup.CUSTOMERS, searchCustomers(tenantId, term, limit)));
        }
        if (groups.contains(SearchGroup.LEADS) && permissions.contains(TenantPermissions.LEAD_READ)) {
            results.add(new SearchGroupResponse(SearchGroup.LEADS, searchLeads(tenantId, term, limit)));
        }
        if (groups.contains(SearchGroup.QUOTATIONS) && permissions.contains(TenantPermissions.QUOTATION_READ)) {
            results.add(new SearchGroupResponse(SearchGroup.QUOTATIONS, searchQuotations(tenantId, term, limit)));
        }
        if (groups.contains(SearchGroup.BOOKINGS) && permissions.contains(TenantPermissions.BOOKING_READ)) {
            results.add(new SearchGroupResponse(SearchGroup.BOOKINGS, searchBookings(tenantId, term, limit)));
        }
        return new SearchResponse(List.copyOf(results));
    }

    private List<SearchResult> searchCustomers(UUID tenantId, String term, int limit) {
        Specification<Customer> specification = this.<Customer>tenantActive(tenantId)
                .and(this.<Customer>textContains(term, "name", "email", "phone"));
        return customerRepository.findAll(specification, page(limit)).stream()
                .map(customer -> new SearchResult(
                        customer.getId(),
                        customer.getName(),
                        customer.getCity() == null ? "Customer" : customer.getCity(),
                        null
                ))
                .toList();
    }

    private List<SearchResult> searchLeads(UUID tenantId, String term, int limit) {
        Specification<Lead> specification = this.<Lead>tenantActive(tenantId)
                .and(this.<Lead>textContains(term, "name", "email", "phone"));
        return leadRepository.findAll(specification, page(limit)).stream()
                .map(lead -> new SearchResult(
                        lead.getId(),
                        lead.getName(),
                        lead.getDestination() == null ? dateLabel(lead.getTravelDate()) : lead.getDestination(),
                        lead.getStatus().name()
                ))
                .toList();
    }

    private List<SearchResult> searchQuotations(UUID tenantId, String term, int limit) {
        Specification<Quotation> specification = this.<Quotation>tenantActive(tenantId)
                .and(this.<Quotation>textContains(term, "quotationNumber"));
        return quotationRepository.findAll(specification, page(limit)).stream()
                .map(quotation -> new SearchResult(
                        quotation.getId(),
                        quotation.getQuotationNumber(),
                        "Quotation",
                        quotation.getStatus().name()
                ))
                .toList();
    }

    private List<SearchResult> searchBookings(UUID tenantId, String term, int limit) {
        Specification<Booking> specification = this.<Booking>tenantActive(tenantId)
                .and(this.<Booking>textContains(term, "bookingNumber"));
        return bookingRepository.findAll(specification, page(limit)).stream()
                .map(booking -> new SearchResult(
                        booking.getId(),
                        booking.getBookingNumber(),
                        dateRange(booking.getTravelStart(), booking.getTravelEnd()),
                        booking.getStatus().name()
                ))
                .toList();
    }

    private <T> Specification<T> tenantActive(UUID tenantId) {
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("tenantId"), tenantId),
                criteriaBuilder.isTrue(root.get("active"))
        );
    }

    private <T> Specification<T> textContains(String term, String... fields) {
        String pattern = "%" + escapeLike(term) + "%";
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                java.util.Arrays.stream(fields)
                        .map(field -> like(root, criteriaBuilder, field, pattern))
                        .toArray(Predicate[]::new)
        );
    }

    private <T> Predicate like(Root<T> root, CriteriaBuilder builder, String field, String pattern) {
        return builder.like(builder.lower(root.get(field).as(String.class)), pattern, '\\');
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private PageRequest page(int limit) {
        return PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String dateLabel(LocalDate date) {
        return date == null ? "Lead" : date.toString();
    }

    private String dateRange(LocalDate start, LocalDate end) {
        if (start == null) return "Booking";
        return end == null ? start.toString() : start + " to " + end;
    }
}
