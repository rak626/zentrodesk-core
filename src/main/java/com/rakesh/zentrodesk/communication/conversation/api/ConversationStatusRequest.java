package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import jakarta.validation.constraints.NotNull;

public record ConversationStatusRequest(@NotNull ConversationMessageStatus status) {
}
