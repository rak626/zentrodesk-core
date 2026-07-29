package com.rakesh.zentrodesk.communication.conversation.api;

import java.util.Set;
import java.util.UUID;

public record ConversationAssignmentRequest(UUID primaryAssigneeId, Set<UUID> collaboratorIds) {

    public ConversationAssignmentRequest {
        collaboratorIds = collaboratorIds == null ? Set.of() : Set.copyOf(collaboratorIds);
    }
}
