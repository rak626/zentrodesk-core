package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.communication.conversation.ConversationAssignmentRole;

import java.util.UUID;

public record ConversationAssignmentResponse(
        UUID userId,
        String userName,
        ConversationAssignmentRole role
) {
}
