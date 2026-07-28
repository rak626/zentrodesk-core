package com.rakesh.zentrodesk.operations.assignment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverAssignmentRepository
        extends JpaRepository<DriverAssignment, UUID>, JpaSpecificationExecutor<DriverAssignment> {

    Optional<DriverAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DriverAssignment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from DriverAssignment assignment
            where assignment.id = :id and assignment.tenantId = :tenantId
            """)
    Optional<DriverAssignment> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    @Query("""
            select assignment
            from DriverAssignment assignment
            where assignment.tenantId = :tenantId
              and assignment.active = true
              and assignment.status in :statuses
              and assignment.pickupAt < :dropoffAt
              and assignment.dropoffAt > :pickupAt
              and (:excludedId is null or assignment.id <> :excludedId)
              and (assignment.driver.id = :driverId or assignment.vehicle.id = :vehicleId)
            """)
    List<DriverAssignment> findConflicts(
            @Param("tenantId") UUID tenantId,
            @Param("driverId") UUID driverId,
            @Param("vehicleId") UUID vehicleId,
            @Param("pickupAt") Instant pickupAt,
            @Param("dropoffAt") Instant dropoffAt,
            @Param("excludedId") UUID excludedId,
            @Param("statuses") Collection<AssignmentStatus> statuses
    );

    boolean existsByTenantIdAndDriverIdAndActiveTrueAndStatusIn(
            UUID tenantId,
            UUID driverId,
            Collection<AssignmentStatus> statuses
    );

    boolean existsByTenantIdAndVehicleIdAndActiveTrueAndStatusIn(
            UUID tenantId,
            UUID vehicleId,
            Collection<AssignmentStatus> statuses
    );

    List<DriverAssignment> findByTenantIdAndBookingIdAndActiveTrueAndStatusIn(
            UUID tenantId,
            UUID bookingId,
            Collection<AssignmentStatus> statuses
    );

    @Query("""
            select assignment
            from DriverAssignment assignment
            where assignment.tenantId = :tenantId
              and assignment.active = true
              and assignment.status in :statuses
              and assignment.pickupAt < :dropoffAt
              and assignment.dropoffAt > :pickupAt
            order by assignment.pickupAt
            """)
    List<DriverAssignment> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("pickupAt") Instant pickupAt,
            @Param("dropoffAt") Instant dropoffAt,
            @Param("statuses") Collection<AssignmentStatus> statuses
    );
}
