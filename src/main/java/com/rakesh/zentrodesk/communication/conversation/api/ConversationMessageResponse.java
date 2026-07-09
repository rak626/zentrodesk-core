package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageResponse(UUID id, UUID customerId, String phone, ConversationMessageDirection direction,
                                          String message, String providerMessageId, ConversationMessageStatus status,
                                          boolean active, Instant createdAt, Instant updatedAt) {
}
