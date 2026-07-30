package com.rakesh.zentrodesk.communication.conversation.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConversationAssignmentRequest(@NotNull UUID primaryAssigneeId) {
}
