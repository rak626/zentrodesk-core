package com.rakesh.zentrodesk.communication.whatsapp;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "whatsapp_configurations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_whatsapp_configuration_tenant", columnNames = "tenant_id"),
                @UniqueConstraint(name = "uk_whatsapp_configuration_waba", columnNames = "waba_id"),
                @UniqueConstraint(
                        name = "uk_whatsapp_configuration_phone_number",
                        columnNames = "phone_number_id"
                )
        }
)
public class WhatsAppConfiguration extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "waba_id", nullable = false, length = 100)
    private String wabaId;

    @Column(name = "business_id", nullable = false, length = 100)
    private String businessId;

    @Column(name = "phone_number_id", nullable = false, length = 100)
    private String phoneNumberId;

    @Column(name = "display_phone_number", nullable = false, length = 32)
    private String displayPhoneNumber;

    @Column(name = "verified_name", length = 150)
    private String verifiedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_method", nullable = false, length = 30)
    private WhatsAppConnectionMethod connectionMethod;

    @Column(name = "access_token_ciphertext", columnDefinition = "TEXT")
    private String accessTokenCiphertext;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_status", nullable = false, length = 20)
    private WhatsAppFundingStatus fundingStatus;

    @Column(name = "token_healthy", nullable = false)
    private boolean tokenHealthy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppConnectionStatus status;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_healthy_at")
    private Instant lastHealthyAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    @Column(nullable = false)
    private long version;
}
