package com.rakesh.zentrodesk.operations.task.api;

import com.rakesh.zentrodesk.operations.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(UUID id, UUID userId, String title, String description, Instant dueAt, TaskStatus status,
                           boolean active, Instant createdAt, Instant updatedAt) {
}
