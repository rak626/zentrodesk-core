package com.rakesh.zentrodesk.communication.conversation;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConversationAssignmentId implements Serializable {

    private UUID conversationId;

    private UUID userId;
}
