package com.rakesh.zentrodesk.sales.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndNormalizedPhoneAndActiveTrue(UUID tenantId, String normalizedPhone);

    List<Customer> findAllByTenantIdAndNormalizedEmailAndActiveTrue(UUID tenantId, String normalizedEmail);

    boolean existsByTenantIdAndNormalizedPhoneAndActiveTrue(UUID tenantId, String normalizedPhone);

    boolean existsByTenantIdAndNormalizedPhoneAndActiveTrueAndIdNot(
            UUID tenantId,
            String normalizedPhone,
            UUID id
    );
}
