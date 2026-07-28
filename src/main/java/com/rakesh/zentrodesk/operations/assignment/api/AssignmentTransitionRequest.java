package com.rakesh.zentrodesk.operations.assignment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignmentTransitionRequest(
        @NotNull Long version,
        @Size(max = 500) String reason
) {
}
