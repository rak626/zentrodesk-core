package com.rakesh.zentrodesk.platform.email;

public record EmailDeliveryMessage(
        String recipient,
        String subject,
        String actionUrl
) {
}
