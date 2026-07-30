package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.tenant.TenantRepository;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.customer.SalesReferenceService;
import com.rakesh.zentrodesk.sales.lead.Lead;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import com.rakesh.zentrodesk.sales.quotation.Quotation;
import com.rakesh.zentrodesk.sales.quotation.QuotationItem;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemResource;
import com.rakesh.zentrodesk.sales.quotation.QuotationItemResourceRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationRepository;
import com.rakesh.zentrodesk.sales.quotation.QuotationPricingBasis;
import com.rakesh.zentrodesk.sales.quotation.QuotationResourceType;
import com.rakesh.zentrodesk.sales.quotation.QuotationChangedEvent;
import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import com.rakesh.zentrodesk.sales.quotation.QuotationSendRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
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
    private final QuotationItemResourceRepository resourceRepository;
    private final LeadRepository leadRepository;
    private final SalesReferenceService salesReferenceService;
    private final CurrentTenant currentTenant;
    private final TenantRepository tenantRepository;
    private final JdbcClient jdbcClient;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

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
        UUID ownerId = currentTenant.userId();
        UUID previousOwnerId = leadOwner(request.leadId(), tenantId);
        Quotation quotation = quotationRepository.save(Quotation.builder()
                .tenantId(tenantId)
                .leadId(request.leadId())
                .customerId(request.customerId())
                .assignedUserId(ownerId)
                .quotationNumber(nextNumber(tenantId))
                .seriesId(UUID.randomUUID())
                .revisionNumber(1)
                .superseded(false)
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
        publishOwnership(quotation, previousOwnerId, ownerId, "QUOTATION_CREATED");
        publishChanged(quotation);
        return toResponse(quotation);
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION", action = "CREATE_REVISION")
    public QuotationResponse createRevision(UUID id) {
        UUID tenantId = currentTenant.id();
        Quotation source = quotationRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        if (source.getStatus() == QuotationStatus.DRAFT) {
            throw new ConflictException("Draft quotation can be edited without creating a revision");
        }
        if (source.getStatus() == QuotationStatus.ACCEPTED) {
            throw new ConflictException("Accepted quotation cannot be revised");
        }
        if (source.isSuperseded()) {
            throw new ConflictException("Only the latest quotation revision can be revised");
        }

        UUID ownerId = currentTenant.userId();
        UUID previousOwnerId = source.getAssignedUserId();
        Quotation revision = quotationRepository.save(Quotation.builder()
                .tenantId(source.getTenantId())
                .leadId(source.getLeadId())
                .customerId(source.getCustomerId())
                .assignedUserId(ownerId)
                .quotationNumber(source.getQuotationNumber())
                .seriesId(source.getSeriesId())
                .revisionNumber(source.getRevisionNumber() + 1)
                .revisedFromId(source.getId())
                .superseded(false)
                .travelStart(source.getTravelStart())
                .travelEnd(source.getTravelEnd())
                .adults(source.getAdults())
                .children(source.getChildren())
                .subtotal(source.getSubtotal())
                .discount(source.getDiscount())
                .tax(source.getTax())
                .total(source.getTotal())
                .validUntil(source.getValidUntil())
                .status(QuotationStatus.DRAFT)
                .notes(source.getNotes())
                .active(true)
                .build());
        source.setSuperseded(true);
        cloneItems(source, revision);
        publishOwnership(revision, previousOwnerId, ownerId, "QUOTATION_REVISION_CREATED");
        publishChanged(revision);
        return toResponse(revision);
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
        publishChanged(quotation);
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
        if (quotation.isSuperseded()) {
            throw new ConflictException("Superseded quotation revision cannot change status");
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
        if (request.status() == QuotationStatus.SENT) {
            eventPublisher.publishEvent(new QuotationSendRequestedEvent(
                    quotation.getTenantId(),
                    quotation.getId(),
                    Instant.now()
            ));
        }
        return toResponse(quotation);
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION", action = "DISABLE")
    public void disable(UUID id) {
        getQuotation(id).setActive(false);
    }

    @Transactional
    @AuditMutation(entityType = "QUOTATION", action = "ASSIGN")
    public QuotationResponse assign(UUID id, QuotationAssignRequest request) {
        Quotation quotation = getQuotation(id);
        assertVersion(quotation.getVersion(), request.version());
        validateAssignedUser(request.assignedUserId(), quotation.getTenantId());
        publishOwnership(
                quotation,
                quotation.getAssignedUserId(),
                request.assignedUserId(),
                "OWNER_REASSIGNED"
        );
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
        String timezone = tenantRepository.findById(tenantId)
                .map(tenant -> tenant.getTimezone())
                .filter(value -> value != null && !value.isBlank())
                .orElse(ZoneId.systemDefault().getId());
        request.items().forEach(item ->
                validateItemSchedule(item, request.travelStart(), request.travelEnd(), ZoneId.of(timezone)));
    }

    private void validateAssignedUser(UUID userId, UUID tenantId) {
        if (!userRepository.existsByIdAndTenantId(userId, tenantId)) {
            throw new ResourceNotFoundException("Assigned user not found");
        }
    }

    private UUID leadOwner(UUID leadId, UUID tenantId) {
        if (leadId == null) {
            return null;
        }
        return leadRepository.findByIdAndTenantId(leadId, tenantId)
                .map(Lead::getAssignedUserId)
                .orElse(null);
    }

    private void publishOwnership(
            Quotation quotation,
            UUID previousOwnerId,
            UUID ownerId,
            String reason
    ) {
        eventPublisher.publishEvent(new OwnershipChangedEvent(
                quotation.getTenantId(),
                previousOwnerId,
                ownerId,
                currentTenant.userId(),
                reason,
                "QUOTATION",
                quotation.getId(),
                quotation.getLeadId(),
                quotation.getId(),
                null,
                null,
                quotation.getCustomerId(),
                Instant.now()
        ));
    }

    private void publishChanged(Quotation quotation) {
        eventPublisher.publishEvent(new QuotationChangedEvent(
                quotation.getTenantId(),
                quotation.getId(),
                Instant.now()
        ));
    }

    private Totals calculateTotals(QuotationRequest request) {
        BigDecimal subtotal = request.items().stream()
                .map(item -> calculateItem(item, request.adults(), request.children()).amount())
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
            CalculatedItem calculated = calculateItem(request, quotation.getAdults(), quotation.getChildren());
            QuotationItemScheduleRequest schedule = request.schedule();
            QuotationItem item = itemRepository.save(QuotationItem.builder()
                    .quotation(quotation)
                    .position(position)
                    .type(request.type())
                    .pricingBasis(calculated.pricingBasis())
                    .description(request.description().trim())
                    .unit(trimToNull(request.unit()))
                    .quantity(effectiveQuantity(request))
                    .unitPrice(calculated.unitPrice())
                    .chargeablePeople(calculated.chargeablePeople())
                    .chargeablePeopleOverridden(calculated.chargeablePeopleOverridden())
                    .vehicleCapacity(calculated.vehicleCapacity())
                    .vehicleCount(calculated.vehicleCount())
                    .vehicleCountOverridden(calculated.vehicleCountOverridden())
                    .checkIn(schedule == null ? null : schedule.checkIn())
                    .checkOut(schedule == null ? null : schedule.checkOut())
                    .startsAt(schedule == null ? null : schedule.startsAt())
                    .endsAt(schedule == null ? null : schedule.endsAt())
                    .pickupLocation(schedule == null ? null : trimToNull(schedule.pickupLocation()))
                    .dropoffLocation(schedule == null ? null : trimToNull(schedule.dropoffLocation()))
                    .capacityOverridden(Boolean.TRUE.equals(request.capacityOverride()))
                    .amount(calculated.amount())
                    .remarks(trimToNull(request.remarks()))
                    .active(true)
                    .build());
            saveResources(item, request.resourceIds());
        }
        itemRepository.flush();
    }

    private void cloneItems(Quotation source, Quotation revision) {
        itemRepository.findByQuotationIdAndActiveTrueOrderByPosition(source.getId()).forEach(item -> {
            QuotationItem cloned = itemRepository.save(QuotationItem.builder()
                        .quotation(revision)
                        .position(item.getPosition())
                        .type(item.getType())
                        .pricingBasis(item.getPricingBasis())
                        .description(item.getDescription())
                        .unit(item.getUnit())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .chargeablePeople(item.getChargeablePeople())
                        .chargeablePeopleOverridden(item.isChargeablePeopleOverridden())
                        .vehicleCapacity(item.getVehicleCapacity())
                        .vehicleCount(item.getVehicleCount())
                        .vehicleCountOverridden(item.isVehicleCountOverridden())
                        .checkIn(item.getCheckIn())
                        .checkOut(item.getCheckOut())
                        .startsAt(item.getStartsAt())
                        .endsAt(item.getEndsAt())
                        .pickupLocation(item.getPickupLocation())
                        .dropoffLocation(item.getDropoffLocation())
                        .capacityOverridden(item.isCapacityOverridden())
                        .amount(item.getAmount())
                        .remarks(item.getRemarks())
                        .active(true)
                        .build());
            resourceRepository.findByQuotationItemIdAndActiveTrueOrderByCreatedAt(item.getId())
                    .forEach(resource -> resourceRepository.save(QuotationItemResource.builder()
                            .quotationItem(cloned)
                            .resourceType(resource.getResourceType())
                            .resourceId(resource.getResourceId())
                            .active(true)
                            .build()));
        });
        itemRepository.flush();
    }

    private CalculatedItem calculateItem(
            QuotationItemRequest item,
            Integer adults,
            Integer children
    ) {
        QuotationPricingBasis pricingBasis =
                item.pricingBasis() == null ? QuotationPricingBasis.FIXED : item.pricingBasis();
        BigDecimal unitPrice = money(item.unitPrice());
        int quantity = effectiveQuantity(item);
        BigDecimal multiplier = BigDecimal.valueOf(quantity);
        Integer chargeablePeople = null;
        boolean chargeablePeopleOverridden = false;
        Integer vehicleCapacity = null;
        Integer vehicleCount = null;
        boolean vehicleCountOverridden = false;

        if (pricingBasis == QuotationPricingBasis.PER_PERSON) {
            chargeablePeopleOverridden = item.chargeablePeople() != null;
            chargeablePeople = chargeablePeopleOverridden ? item.chargeablePeople() : adults;
            if (chargeablePeople == null || chargeablePeople <= 0) {
                throw new BadRequestException("Per-person item requires at least one chargeable adult");
            }
            multiplier = multiplier.multiply(BigDecimal.valueOf(chargeablePeople));
        } else if (pricingBasis == QuotationPricingBasis.PER_VEHICLE) {
            List<UUID> resourceIds = resources(item);
            vehicleCapacity = item.vehicleCapacity();
            vehicleCountOverridden = !resourceIds.isEmpty() || item.vehicleCount() != null;
            if (!resourceIds.isEmpty()) {
                vehicleCount = resourceIds.size();
            } else {
                if (vehicleCapacity == null || vehicleCapacity <= 0) {
                    throw new BadRequestException("Per-vehicle item requires passenger capacity");
                }
                vehicleCount = item.vehicleCount() != null
                        ? item.vehicleCount()
                        : requiredVehicles(adults, children, vehicleCapacity);
            }
            multiplier = multiplier.multiply(BigDecimal.valueOf(vehicleCount));
        }

        return new CalculatedItem(
                pricingBasis,
                unitPrice,
                chargeablePeople,
                chargeablePeopleOverridden,
                vehicleCapacity,
                vehicleCount,
                vehicleCountOverridden,
                unitPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private int requiredVehicles(Integer adults, Integer children, int capacity) {
        int passengers = Math.max(0, adults == null ? 0 : adults)
                + Math.max(0, children == null ? 0 : children);
        if (passengers == 0) {
            throw new BadRequestException("Per-vehicle item requires at least one passenger");
        }
        return (passengers + capacity - 1) / capacity;
    }

    private int effectiveQuantity(QuotationItemRequest item) {
        if (item.type() == com.rakesh.zentrodesk.sales.quotation.QuotationItemType.HOTEL
                && item.schedule() != null
                && item.schedule().checkIn() != null
                && item.schedule().checkOut() != null) {
            return Math.toIntExact(ChronoUnit.DAYS.between(
                    item.schedule().checkIn(),
                    item.schedule().checkOut()
            ));
        }
        return item.quantity();
    }

    private void validateItemSchedule(
            QuotationItemRequest item,
            LocalDate travelStart,
            LocalDate travelEnd,
            ZoneId timezone
    ) {
        List<UUID> resources = resources(item);
        if (resources.size() != new HashSet<>(resources).size()) {
            throw new BadRequestException("Quotation item resources must be unique");
        }
        QuotationItemScheduleRequest schedule = item.schedule();
        switch (item.type()) {
            case HOTEL -> {
                if (schedule == null || schedule.checkIn() == null || schedule.checkOut() == null) {
                    throw new BadRequestException("Hotel item requires check-in and check-out dates");
                }
                if (!schedule.checkIn().isBefore(schedule.checkOut())) {
                    throw new BadRequestException("Hotel check-out must be after check-in");
                }
                if (schedule.checkIn().isBefore(travelStart) || schedule.checkOut().isAfter(travelEnd)) {
                    throw new BadRequestException("Hotel stay must be within quotation travel dates");
                }
                if (resources.isEmpty()) {
                    throw new BadRequestException("Hotel item requires at least one selected room");
                }
            }
            case VEHICLE -> {
                validateTimedSchedule(schedule, travelStart, travelEnd, timezone, "Vehicle");
                if (trimToNull(schedule.pickupLocation()) == null
                        || trimToNull(schedule.dropoffLocation()) == null) {
                    throw new BadRequestException("Vehicle item requires pickup and drop-off locations");
                }
                if (resources.isEmpty()) {
                    throw new BadRequestException("Vehicle item requires at least one selected vehicle");
                }
            }
            case GUIDE -> {
                validateTimedSchedule(schedule, travelStart, travelEnd, timezone, "Guide");
                if (resources.isEmpty()) {
                    throw new BadRequestException("Guide item requires at least one selected guide");
                }
            }
            default -> {
                if (!resources.isEmpty()) {
                    throw new BadRequestException("Only hotel, vehicle, and guide items can select resources");
                }
            }
        }
    }

    private void validateTimedSchedule(
            QuotationItemScheduleRequest schedule,
            LocalDate travelStart,
            LocalDate travelEnd,
            ZoneId timezone,
            String label
    ) {
        if (schedule == null || schedule.startsAt() == null || schedule.endsAt() == null) {
            throw new BadRequestException(label + " item requires start and end times");
        }
        if (!schedule.startsAt().isBefore(schedule.endsAt())) {
            throw new BadRequestException(label + " end time must be after start time");
        }
        LocalDate startsOn = schedule.startsAt().atZone(timezone).toLocalDate();
        LocalDate endsOn = schedule.endsAt().atZone(timezone).toLocalDate();
        if (startsOn.isBefore(travelStart) || endsOn.isAfter(travelEnd)) {
            throw new BadRequestException(label + " schedule must be within quotation travel dates");
        }
    }

    private List<UUID> resources(QuotationItemRequest item) {
        return item.resourceIds() == null ? List.of() : item.resourceIds();
    }

    private void saveResources(QuotationItem item, List<UUID> resourceIds) {
        QuotationResourceType resourceType = resourceType(item.getType());
        if (resourceType == null) {
            return;
        }
        (resourceIds == null ? List.<UUID>of() : resourceIds).forEach(resourceId ->
                resourceRepository.save(QuotationItemResource.builder()
                        .quotationItem(item)
                        .resourceType(resourceType)
                        .resourceId(resourceId)
                        .active(true)
                        .build())
        );
    }

    private QuotationResourceType resourceType(
            com.rakesh.zentrodesk.sales.quotation.QuotationItemType itemType
    ) {
        return switch (itemType) {
            case HOTEL -> QuotationResourceType.ROOM;
            case VEHICLE -> QuotationResourceType.VEHICLE;
            case GUIDE -> QuotationResourceType.GUIDE;
            default -> null;
        };
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
                                item.getPricingBasis(),
                                item.getDescription(),
                                item.getUnit(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getChargeablePeople(),
                                item.isChargeablePeopleOverridden(),
                                item.getVehicleCapacity(),
                                item.getVehicleCount(),
                                item.isVehicleCountOverridden(),
                                item.getCheckIn(),
                                item.getCheckOut(),
                                item.getStartsAt(),
                                item.getEndsAt(),
                                item.getPickupLocation(),
                                item.getDropoffLocation(),
                                item.isCapacityOverridden(),
                                resourceRepository.findByQuotationItemIdAndActiveTrueOrderByCreatedAt(item.getId())
                                        .stream()
                                        .map(QuotationItemResource::getResourceId)
                                        .toList(),
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
                quotation.getAssignedUserId(),
                ownerName(quotation.getAssignedUserId(), quotation.getTenantId()),
                quotation.getQuotationNumber(),
                quotation.getSeriesId(),
                quotation.getRevisionNumber(),
                quotation.getRevisedFromId(),
                quotation.isSuperseded(),
                quotation.getTravelStart(),
                quotation.getTravelEnd(),
                quotation.getAdults(),
                quotation.getChildren(),
                quotation.getSubtotal(),
                quotation.getDiscount(),
                quotation.getTax(),
                quotation.getTotal(),
                perPersonTotal(quotation),
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

    private BigDecimal perPersonTotal(Quotation quotation) {
        return quotation.getAdults() == null || quotation.getAdults() <= 0
                ? null
                : money(quotation.getTotal()).divide(
                        BigDecimal.valueOf(quotation.getAdults()),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private String ownerName(UUID ownerId, UUID tenantId) {
        if (ownerId == null) {
            return null;
        }
        return userRepository.findWithTenantRoleAndPermissionsByIdAndTenantId(ownerId, tenantId)
                .map(user -> (user.getFirstName() + " "
                        + (user.getLastName() == null ? "" : user.getLastName())).trim())
                .orElse(null);
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

    private record CalculatedItem(
            QuotationPricingBasis pricingBasis,
            BigDecimal unitPrice,
            Integer chargeablePeople,
            boolean chargeablePeopleOverridden,
            Integer vehicleCapacity,
            Integer vehicleCount,
            boolean vehicleCountOverridden,
            BigDecimal amount
    ) {
    }
}
