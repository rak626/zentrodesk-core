package com.rakesh.zentrodesk.communication.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    Optional<Template> findByIdAndTenantIdAndType(UUID id, UUID tenantId, TemplateType type);

    Optional<Template> findFirstByTenantIdAndTypeAndStatusAndActiveTrueOrderByTemplateVersionDesc(
            UUID tenantId,
            TemplateType type,
            TemplateStatus status
    );

    List<Template> findAllByTenantIdAndTypeAndStatusAndActiveTrue(
            UUID tenantId,
            TemplateType type,
            TemplateStatus status
    );

    int countByTenantIdAndType(UUID tenantId, TemplateType type);
}
