package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConversationMessageRequest(UUID customerId, @NotBlank @Size(max = 20) String phone,
                                         @NotNull ConversationMessageDirection direction, @NotBlank String message,
                                         @Size(max = 100) String providerMessageId, ConversationMessageStatus status,
                                         Boolean active) {
}
