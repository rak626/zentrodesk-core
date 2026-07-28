package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.sales.customer.SalesReferenceService;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationItem;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt",
            "updatedAt",
            "quotationNumber",
            "validUntil",
            "total",
            "status",
            "active"
    );
    private static final Map<QuotationStatus, Set<QuotationStatus>> TRANSITIONS = Map.of(
            QuotationStatus.DRAFT, Set.of(QuotationStatus.SENT),
            QuotationStatus.SENT, Set.of(
                    QuotationStatus.VIEWED,
                    QuotationStatus.ACCEPTED,
                    QuotationStatus.REJECTED,
                    QuotationStatus.EXPIRED
            ),
            QuotationStatus.VIEWED, Set.of(
                    QuotationStatus.ACCEPTED,
                    QuotationStatus.REJECTED,
                    QuotationStatus.EXPIRED
            ),
            QuotationStatus.ACCEPTED, Set.of(),
            QuotationStatus.REJECTED, Set.of(),
            QuotationStatus.EXPIRED, Set.of()
    );

    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository itemRepository;
    private final LeadRepository leadRepository;
    private final SalesReferenceService salesReferenceService;
    private final CurrentTenant currentTenant;
    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public PageResponse<QuotationResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            QuotationStatus status,
            UUID leadId,
            UUID customerId,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Quotation> specification = Specification
                .where(SearchSpecificationBuilder.<Quotation>textContains(
                        search,
                        Set.of("quotationNumber", "notes")
                ))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.uuidEquals("leadId", leadId))
                .and(SearchSpecificationBuilder.uuidEquals("customerId", customerId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status));
        return PageResponse.from(quotationRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public QuotationResponse findById(UUID id) {
        return toResponse(getQuotation(id));
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION", action = "CREATE")
    public QuotationResponse create(QuotationRequest request) {
        UUID tenantId = currentTenant.id();
        validateRequest(request, tenantId);
        Totals totals = calculateTotals(request);
        Quotation quotation = quotationRepository.save(Quotation.builder()
                .tenantId(tenantId)
                .leadId(request.leadId())
                .customerId(request.customerId())
                .quotationNumber(nextNumber(tenantId))
                .travelStart(request.travelStart())
                .travelEnd(request.travelEnd())
                .adults(request.adults())
                .children(request.children())
                .subtotal(totals.subtotal())
                .discount(totals.discount())
                .tax(totals.tax())
                .total(totals.total())
                .validUntil(request.validUntil())
                .status(request.status() == null ? QuotationStatus.DRAFT : request.status())
                .notes(trimToNull(request.notes()))
                .active(request.active() == null || request.active())
                .build());
        replaceItems(quotation, request.items());
        return toResponse(quotation);
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION", action = "UPDATE")
    public QuotationResponse update(UUID id, QuotationRequest request) {
        Quotation quotation = getQuotation(id);
        assertVersion(quotation.getVersion(), request.version());
        validateRequest(request, quotation.getTenantId());
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new ConflictException("Only draft quotations can be edited");
        }
        Totals totals = calculateTotals(request);
        quotation.setLeadId(request.leadId());
        quotation.setCustomerId(request.customerId());
        quotation.setTravelStart(request.travelStart());
        quotation.setTravelEnd(request.travelEnd());
        quotation.setAdults(request.adults());
        quotation.setChildren(request.children());
        quotation.setSubtotal(totals.subtotal());
        quotation.setDiscount(totals.discount());
        quotation.setTax(totals.tax());
        quotation.setTotal(totals.total());
        quotation.setValidUntil(request.validUntil());
        quotation.setNotes(trimToNull(request.notes()));
        if (request.active() != null) {
            quotation.setActive(request.active());
        }
        replaceItems(quotation, request.items());
        return toResponse(quotation);
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION", action = "STATUS_UPDATE")
    public QuotationResponse updateStatus(UUID id, QuotationStatusRequest request) {
        Quotation quotation = getQuotation(id);
        assertVersion(quotation.getVersion(), request.version());
        if (quotation.getStatus() == request.status()) {
            return toResponse(quotation);
        }
        if (quotation.getValidUntil() != null
                && quotation.getValidUntil().isBefore(LocalDate.now())
                && request.status() != QuotationStatus.EXPIRED) {
            throw new ConflictException("Expired quotation can only transition to EXPIRED");
        }
        if (!TRANSITIONS.getOrDefault(quotation.getStatus(), Set.of()).contains(request.status())) {
            throw new BadRequestException(
                    "Quotation cannot transition from " + quotation.getStatus() + " to " + request.status()
            );
        }
        quotation.setStatus(request.status());
        return toResponse(quotation);
    }

    private Quotation getQuotation(UUID id) {
        return quotationRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
    }

    private void validateRequest(QuotationRequest request, UUID tenantId) {
        if (request.leadId() == null && request.customerId() == null) {
            throw new BadRequestException("Quotation must be linked to a lead or customer");
        }
        Lead lead = null;
        if (request.leadId() != null) {
            lead = leadRepository.findByIdAndTenantId(request.leadId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        }
        if (request.customerId() != null) {
            salesReferenceService.requireCustomer(request.customerId(), tenantId);
        }
        if (lead != null
                && lead.getCustomerId() != null
                && request.customerId() != null
                && !lead.getCustomerId().equals(request.customerId())) {
            throw new BadRequestException("Quotation customer does not match lead customer");
        }
        if (request.travelStart() != null
                && request.travelEnd() != null
                && !request.travelEnd().isAfter(request.travelStart())) {
            throw new BadRequestException("Travel end must be after travel start");
        }
    }

    private Totals calculateTotals(QuotationRequest request) {
        BigDecimal subtotal = request.items().stream()
                .map(item -> money(item.unitPrice()).multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = money(request.discount());
        BigDecimal tax = money(request.tax());
        if (discount.compareTo(subtotal) > 0) {
            throw new BadRequestException("Discount cannot exceed subtotal");
        }
        return new Totals(
                subtotal,
                discount,
                tax,
                subtotal.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private void replaceItems(Quotation quotation, List<QuotationItemRequest> requests) {
        List<QuotationItem> existing =
                itemRepository.findByQuotationIdAndActiveTrueOrderByPosition(quotation.getId());
        existing.forEach(item -> item.setActive(false));
        itemRepository.flush();
        for (int position = 0; position < requests.size(); position++) {
            QuotationItemRequest request = requests.get(position);
            BigDecimal unitPrice = money(request.unitPrice());
            itemRepository.save(QuotationItem.builder()
                    .quotation(quotation)
                    .position(position)
                    .type(request.type())
                    .description(request.description().trim())
                    .unit(trimToNull(request.unit()))
                    .quantity(request.quantity())
                    .unitPrice(unitPrice)
                    .amount(unitPrice.multiply(BigDecimal.valueOf(request.quantity()))
                            .setScale(2, RoundingMode.HALF_UP))
                    .remarks(trimToNull(request.remarks()))
                    .active(true)
                    .build());
        }
        itemRepository.flush();
    }

    private String nextNumber(UUID tenantId) {
        Long value = jdbcClient.sql("""
                        insert into zentro_desk.document_counters as counter (tenant_id, document_type, next_value)
                        values (:tenantId, 'QUOTATION', 2)
                        on conflict (tenant_id, document_type)
                        do update set next_value = counter.next_value + 1
                        returning next_value - 1
                        """)
                .param("tenantId", tenantId)
                .query(Long.class)
                .single();
        return "Q-" + LocalDate.now().getYear() + "-" + String.format("%06d", value);
    }

    private QuotationResponse toResponse(Quotation quotation) {
        List<QuotationItemResponse> items =
                itemRepository.findByQuotationIdAndActiveTrueOrderByPosition(quotation.getId()).stream()
                        .map(item -> new QuotationItemResponse(
                                item.getId(),
                                item.getPosition(),
                                item.getType(),
                                item.getDescription(),
                                item.getUnit(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getAmount(),
                                item.getRemarks()
                        ))
                        .toList();
        QuotationStatus effectiveStatus = quotation.getValidUntil() != null
                        && quotation.getValidUntil().isBefore(LocalDate.now())
                        && Set.of(QuotationStatus.SENT, QuotationStatus.VIEWED).contains(quotation.getStatus())
                ? QuotationStatus.EXPIRED
                : quotation.getStatus();
        return new QuotationResponse(
                quotation.getId(),
                quotation.getLeadId(),
                quotation.getCustomerId(),
                quotation.getQuotationNumber(),
                quotation.getTravelStart(),
                quotation.getTravelEnd(),
                quotation.getAdults(),
                quotation.getChildren(),
                quotation.getSubtotal(),
                quotation.getDiscount(),
                quotation.getTax(),
                quotation.getTotal(),
                quotation.getValidUntil(),
                effectiveStatus,
                quotation.getNotes(),
                quotation.isActive(),
                quotation.getVersion(),
                items,
                quotation.getCreatedAt(),
                quotation.getUpdatedAt()
        );
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && expected != current) {
            throw new ConflictException("Quotation was updated by another user");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }
}
