package com.rakesh.zentrodesk.communication.template;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;
import java.time.Instant;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "templates",
        indexes = {
                @Index(name = "idx_template_tenant", columnList = "tenant_id"),
                @Index(name = "idx_template_type", columnList = "type"),
                @Index(name = "idx_template_status", columnList = "status")
        }
)
public class Template extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "style_content", columnDefinition = "TEXT")
    private String styleContent;

    @Column(name = "whatsapp_template_name", length = 512)
    private String whatsAppTemplateName;

    @Column(name = "whatsapp_template_language", length = 10)
    private String whatsAppTemplateLanguage;

    @Default
    @Column(name = "template_version", nullable = false)
    private int templateVersion = 1;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateStatus status;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
