package com.rakesh.zentrodesk.communication.conversation.api;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CustomerConversationCreateRequest(
        UUID leadId,
        UUID customerId,
        @Size(max = 32) String phone,
        @Size(max = 150) String displayName
) {

    public CustomerConversationCreateRequest(UUID customerId, String phone, String displayName) {
        this(null, customerId, phone, displayName);
    }
}
