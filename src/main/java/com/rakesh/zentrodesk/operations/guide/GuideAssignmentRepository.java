package com.rakesh.zentrodesk.operations.guide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuideAssignmentRepository extends JpaRepository<GuideAssignment, UUID> {

    @Query("""
            select assignment
            from GuideAssignment assignment
            where assignment.tenantId = :tenantId
              and assignment.active = true
              and assignment.status in :statuses
              and assignment.startsAt < :endsAt
              and assignment.endsAt > :startsAt
            """)
    List<GuideAssignment> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("statuses") Collection<GuideAssignmentStatus> statuses
    );

    boolean existsByTenantIdAndGuideIdAndActiveTrueAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID tenantId,
            UUID guideId,
            Collection<GuideAssignmentStatus> statuses,
            Instant endsAt,
            Instant startsAt
    );

    boolean existsByTenantIdAndGuideIdAndActiveTrueAndStatusIn(
            UUID tenantId,
            UUID guideId,
            Collection<GuideAssignmentStatus> statuses
    );
}
