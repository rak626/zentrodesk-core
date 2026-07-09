package com.rakesh.zentrodesk.operations.task.api;

import com.rakesh.zentrodesk.operations.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {
}
