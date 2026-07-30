package com.rakesh.zentrodesk.operations.guide.api;

import com.rakesh.zentrodesk.operations.guide.GuideStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GuideRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 20) String phone,
        @Size(max = 500) String languages,
        @NotNull @DecimalMin("0.00") BigDecimal baseRate,
        GuideStatus status,
        Boolean active,
        Long version
) {
}
