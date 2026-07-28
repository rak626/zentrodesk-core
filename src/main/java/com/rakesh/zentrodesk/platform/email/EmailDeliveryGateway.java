package com.rakesh.zentrodesk.platform.email;

public interface EmailDeliveryGateway {

    void deliver(EmailDeliveryMessage message);
}
