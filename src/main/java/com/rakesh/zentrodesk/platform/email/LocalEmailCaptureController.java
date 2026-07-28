package com.rakesh.zentrodesk.platform.email;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/api/dev/emails")
class LocalEmailCaptureController {

    private final LocalEmailDeliveryGateway localEmailDeliveryGateway;

    @GetMapping
    List<EmailDeliveryMessage> findAll() {
        return localEmailDeliveryGateway.capturedMessages();
    }
}
