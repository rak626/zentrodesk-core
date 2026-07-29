package com.rakesh.zentrodesk.communication.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationSendRequest(@NotBlank @Size(max = 4096) String message) {
}
