package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.CustomerConversationStatus;
import jakarta.validation.constraints.NotNull;

public record ConversationStateRequest(@NotNull CustomerConversationStatus status) {
}
