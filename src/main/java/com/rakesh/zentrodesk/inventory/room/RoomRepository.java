package com.rakesh.zentrodesk.inventory.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    long countByPropertyTenantIdAndActiveTrue(UUID tenantId);

    long countByPropertyTenantIdAndStatusAndActiveTrue(UUID tenantId, RoomStatus status);
}
