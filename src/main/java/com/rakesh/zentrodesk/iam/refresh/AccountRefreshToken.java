package com.rakesh.zentrodesk.iam.refresh;

import com.rakesh.common.entity.BaseEntity;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
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
        name = "account_refresh_tokens",
        indexes = {
                @Index(name = "idx_account_refresh_token_account", columnList = "account_type, account_id"),
                @Index(name = "idx_account_refresh_token_hash", columnList = "token_hash"),
                @Index(name = "idx_account_refresh_token_family", columnList = "family_id")
        }
)
public class AccountRefreshToken extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @NotBlank
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "parent_token_id")
    private UUID parentTokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Default
    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 30)
    private RefreshTokenRevocationReason revokedReason;

    @Column(name = "reuse_detected_at")
    private Instant reuseDetectedAt;
}
