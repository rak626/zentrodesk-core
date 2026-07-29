package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.CustomerConversationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerConversationResponse(
        UUID id,
        UUID customerId,
        String phone,
        String displayName,
        CustomerConversationStatus status,
        Instant lastMessageAt,
        Instant lastInboundAt,
        Instant serviceWindowExpiresAt,
        boolean freeformMessagingAvailable,
        int unreadCount,
        List<ConversationAssignmentResponse> assignments,
        long version
) {
}
