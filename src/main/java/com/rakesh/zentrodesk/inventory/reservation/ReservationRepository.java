package com.rakesh.zentrodesk.inventory.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    Optional<Reservation> findByIdAndPropertyTenantId(UUID id, UUID tenantId);

    boolean existsByPropertyIdAndStatusInAndActiveTrue(UUID propertyId, Collection<ReservationStatus> statuses);

    boolean existsByRoomIdAndStatusInAndActiveTrue(UUID roomId, Collection<ReservationStatus> statuses);

    @Query("""
            select reservation
            from Reservation reservation
            join fetch reservation.property property
            join fetch reservation.room room
            where property.tenantId = :tenantId
              and reservation.active = true
              and reservation.status in :statuses
              and reservation.checkIn < :to
              and reservation.checkOut > :from
              and (:propertyId is null or property.id = :propertyId)
              and (:minCapacity is null or room.capacity >= :minCapacity)
            order by reservation.checkIn, room.name
            """)
    List<Reservation> findActiveOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("propertyId") UUID propertyId,
            @Param("minCapacity") Integer minCapacity,
            @Param("statuses") Collection<ReservationStatus> statuses
    );

    @Query("""
            select count(reservation) > 0
            from Reservation reservation
            where reservation.room.id = :roomId
              and reservation.active = true
              and reservation.status in :statuses
              and reservation.checkIn < :checkOut
              and reservation.checkOut > :checkIn
              and (:excludedId is null or reservation.id <> :excludedId)
            """)
    boolean existsActiveOverlap(
            @Param("roomId") UUID roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("excludedId") UUID excludedId,
            @Param("statuses") Collection<ReservationStatus> statuses
    );
}
