package com.rakesh.zentrodesk.operations.task.api;

import com.rakesh.zentrodesk.operations.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record TaskRequest(@NotNull UUID userId, @NotBlank String title, String description, Instant dueAt, TaskStatus status, Boolean active) {
}
