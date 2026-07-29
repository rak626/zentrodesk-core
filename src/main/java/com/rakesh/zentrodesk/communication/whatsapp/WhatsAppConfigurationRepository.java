package com.rakesh.zentrodesk.communication.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WhatsAppConfigurationRepository extends JpaRepository<WhatsAppConfiguration, UUID> {

    Optional<WhatsAppConfiguration> findByTenantId(UUID tenantId);

    Optional<WhatsAppConfiguration> findByPhoneNumberId(String phoneNumberId);

    boolean existsByWabaIdAndTenantIdNot(String wabaId, UUID tenantId);

    boolean existsByPhoneNumberIdAndTenantIdNot(String phoneNumberId, UUID tenantId);
}
