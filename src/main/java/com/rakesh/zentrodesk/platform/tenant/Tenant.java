package com.rakesh.zentrodesk.platform.tenant;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "tenants",
        indexes = {
                @Index(name = "idx_tenant_code", columnList = "code")
        }
)
public class Tenant extends BaseEntity {

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Email
    @Size(max = 150)
    @Column(length = 150)
    private String email;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

    @Size(max = 255)
    @Column(length = 255)
    private String address;

    @Size(max = 100)
    @Column(length = 100)
    private String city;

    @Size(max = 100)
    @Column(length = 100)
    private String state;

    @Size(max = 100)
    @Column(length = 100)
    private String country;

    @Size(max = 50)
    @Column(length = 50)
    private String timezone;

    @Size(max = 10)
    @Column(length = 10)
    private String currency;

    @Column(nullable = false)
    @lombok.Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 30)
    @lombok.Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @NotBlank
    @Size(max = 50)
    @Column(name = "plan_code", nullable = false, length = 50)
    @lombok.Builder.Default
    private String planCode = "STARTER";

    @Column(name = "seat_limit", nullable = false)
    @lombok.Builder.Default
    private int seatLimit = 5;
}
