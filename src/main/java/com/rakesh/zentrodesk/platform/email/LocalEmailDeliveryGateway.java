package com.rakesh.zentrodesk.platform.email;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Profile({"dev", "test"})
public class LocalEmailDeliveryGateway implements EmailDeliveryGateway {

    private final List<EmailDeliveryMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void deliver(EmailDeliveryMessage message) {
        messages.add(message);
    }

    public List<EmailDeliveryMessage> capturedMessages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
