package com.rakesh.zentrodesk.operations.guide.api;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.common.pagination.PaginationUtils;
import com.rakesh.zentrodesk.common.search.SearchSpecificationBuilder;
import com.rakesh.zentrodesk.operations.guide.Guide;
import com.rakesh.zentrodesk.operations.guide.GuideAssignment;
import com.rakesh.zentrodesk.operations.guide.GuideAssignmentRepository;
import com.rakesh.zentrodesk.operations.guide.GuideAssignmentStatus;
import com.rakesh.zentrodesk.operations.guide.GuideRepository;
import com.rakesh.zentrodesk.operations.guide.GuideStatus;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.operations.booking.Booking;
import com.rakesh.zentrodesk.operations.booking.BookingRepository;
import com.rakesh.zentrodesk.operations.booking.BookingResourcesRequestedEvent;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuideService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "updatedAt", "name", "baseRate", "status", "active");
    private static final Set<GuideAssignmentStatus> OCCUPYING_STATUSES =
            Set.of(GuideAssignmentStatus.RESERVED);

    private final GuideRepository guideRepository;
    private final GuideAssignmentRepository assignmentRepository;
    private final BookingRepository bookingRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public PageResponse<GuideResponse> findAll(
            int page,
            int size,
            String sort,
            String direction,
            String search,
            GuideStatus status,
            Boolean active
    ) {
        UUID tenantId = currentTenant.id();
        Pageable pageable = PaginationUtils.pageable(page, size, sort, direction, ALLOWED_SORTS);
        Specification<Guide> specification = Specification
                .where(SearchSpecificationBuilder.<Guide>textContains(search, Set.of("name", "phone", "languages")))
                .and(SearchSpecificationBuilder.uuidEquals("tenantId", tenantId))
                .and(SearchSpecificationBuilder.booleanEquals("active", active))
                .and((root, query, cb) -> status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status));
        return PageResponse.from(guideRepository.findAll(specification, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public GuideAvailabilityResponse availability(Instant from, Instant to) {
        validateRange(from, to);
        UUID tenantId = currentTenant.id();
        Set<UUID> occupied = assignmentRepository
                .findOverlapping(tenantId, from, to, OCCUPYING_STATUSES)
                .stream()
                .map(assignment -> assignment.getGuide().getId())
                .collect(java.util.stream.Collectors.toSet());
        return new GuideAvailabilityResponse(
                from,
                to,
                guideRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                        .map(guide -> new AvailableGuideResponse(
                                guide.getId(),
                                guide.getName(),
                                guide.getLanguages(),
                                guide.getBaseRate(),
                                guide.getStatus(),
                                guide.getStatus() == GuideStatus.AVAILABLE
                                        && !occupied.contains(guide.getId())
                        ))
                        .toList()
        );
    }

    @Transactional
    @AuditMutation(entityType = "GUIDE", action = "CREATE")
    public GuideResponse create(GuideRequest request) {
        Guide guide = Guide.builder()
                .tenantId(currentTenant.id())
                .name(request.name().trim())
                .phone(trimToNull(request.phone()))
                .languages(trimToNull(request.languages()))
                .baseRate(request.baseRate().setScale(2, RoundingMode.HALF_UP))
                .status(request.status() == null ? GuideStatus.AVAILABLE : request.status())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(guideRepository.save(guide));
    }

    @EventListener
    public void assignQuotationGuides(BookingResourcesRequestedEvent event) {
        if (event.guides().isEmpty()) {
            return;
        }
        Booking booking = bookingRepository.findByIdAndTenantId(event.bookingId(), event.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        event.guides().forEach(block -> {
            Guide guide = guideRepository.findByIdAndTenantId(block.guideId(), event.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Selected guide not found"));
            validateRange(block.startsAt(), block.endsAt());
            if (!guide.isActive() || guide.getStatus() != GuideStatus.AVAILABLE) {
                throw new ConflictException("Selected guide is unavailable");
            }
            if (assignmentRepository
                    .existsByTenantIdAndGuideIdAndActiveTrueAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
                            event.tenantId(),
                            guide.getId(),
                            OCCUPYING_STATUSES,
                            block.endsAt(),
                            block.startsAt()
                    )) {
                throw new ConflictException("Selected guide is unavailable for the selected time");
            }
            try {
                assignmentRepository.saveAndFlush(GuideAssignment.builder()
                        .tenantId(event.tenantId())
                        .booking(booking)
                        .guide(guide)
                        .startsAt(block.startsAt())
                        .endsAt(block.endsAt())
                        .status(GuideAssignmentStatus.RESERVED)
                        .active(true)
                        .build());
            } catch (DataIntegrityViolationException exception) {
                throw new ConflictException("Selected guide is unavailable for the selected time");
            }
        });
    }

    @Transactional
    @AuditMutation(entityType = "GUIDE", action = "UPDATE")
    public GuideResponse update(UUID id, GuideRequest request) {
        Guide guide = getGuide(id);
        assertVersion(guide.getVersion(), request.version());
        guide.setName(request.name().trim());
        guide.setPhone(trimToNull(request.phone()));
        guide.setLanguages(trimToNull(request.languages()));
        guide.setBaseRate(request.baseRate().setScale(2, RoundingMode.HALF_UP));
        if (request.status() != null) {
            guide.setStatus(request.status());
        }
        if (request.active() != null) {
            guide.setActive(request.active());
        }
        return toResponse(guide);
    }

    @Transactional
    @AuditMutation(entityType = "GUIDE", action = "DISABLE")
    public void disable(UUID id) {
        Guide guide = getGuide(id);
        if (assignmentRepository.existsByTenantIdAndGuideIdAndActiveTrueAndStatusIn(
                currentTenant.id(),
                guide.getId(),
                OCCUPYING_STATUSES
        )) {
            throw new ConflictException("Guide has an active assignment");
        }
        guide.setActive(false);
    }

    private Guide getGuide(UUID id) {
        return guideRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Guide not found"));
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BadRequestException("Guide availability end must be after start");
        }
    }

    private void assertVersion(long current, Long expected) {
        if (expected != null && current != expected) {
            throw new ConflictException("Guide was updated by another user");
        }
    }

    private GuideResponse toResponse(Guide guide) {
        return new GuideResponse(
                guide.getId(),
                guide.getName(),
                guide.getPhone(),
                guide.getLanguages(),
                guide.getBaseRate(),
                guide.getStatus(),
                guide.isActive(),
                guide.getVersion(),
                guide.getCreatedAt(),
                guide.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
