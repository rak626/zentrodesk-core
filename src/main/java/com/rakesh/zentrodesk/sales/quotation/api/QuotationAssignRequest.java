package com.rakesh.zentrodesk.sales.quotation.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuotationAssignRequest(@NotNull UUID assignedUserId, Long version) {
}
