package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.ConversationMessageDirection;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageStatus;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageType;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageResponse(UUID id, UUID conversationId, UUID customerId, UUID sentByUserId,
                                          String phone, ConversationMessageDirection direction, String message,
                                          ConversationMessageType messageType, String providerMessageId,
                                          ConversationMessageStatus status,
                                          boolean active, Instant createdAt, Instant updatedAt) {
}
