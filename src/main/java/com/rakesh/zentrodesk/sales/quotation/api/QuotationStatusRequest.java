package com.rakesh.zentrodesk.sales.quotation.api;

import com.rakesh.zentrodesk.sales.quotation.QuotationStatus;
import jakarta.validation.constraints.NotNull;

public record QuotationStatusRequest(@NotNull QuotationStatus status, Long version) {
}
