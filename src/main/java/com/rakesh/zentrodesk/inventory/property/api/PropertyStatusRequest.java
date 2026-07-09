package com.rakesh.zentrodesk.inventory.property.api;

import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import jakarta.validation.constraints.NotNull;

public record PropertyStatusRequest(@NotNull PropertyStatus status) {
}
