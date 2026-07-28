package com.rakesh.zentrodesk.sales.lead.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowUpCompletionRequest(
        @NotBlank @Size(max = 1000) String outcome,
        Long version
) {
}
