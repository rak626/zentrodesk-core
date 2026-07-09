package com.rakesh.zentrodesk.sales.customer;

import com.rakesh.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
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

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "customers",
        indexes = {
                @Index(name = "idx_customer_tenant", columnList = "tenant_id"),
                @Index(name = "idx_customer_phone", columnList = "phone"),
                @Index(name = "idx_customer_email", columnList = "email")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customer_tenant_phone",
                        columnNames = {"tenant_id", "phone"}
                )
        }
)
public class Customer extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

    @Email
    @Size(max = 150)
    @Column(length = 150)
    private String email;

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

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Default
    @Column(nullable = false)
    private boolean active = true;
}
