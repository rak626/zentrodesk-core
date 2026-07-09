package com.rakesh.zentrodesk.sales.lead.api;

import com.rakesh.zentrodesk.sales.lead.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record LeadStatusRequest(@NotNull LeadStatus status) {
}
