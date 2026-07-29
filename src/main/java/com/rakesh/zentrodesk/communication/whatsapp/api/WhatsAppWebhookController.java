package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService service;

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String token,
            @RequestParam(name = "hub.challenge") String challenge
    ) {
        return service.verifyChallenge(mode, token)
                ? ResponseEntity.ok(challenge)
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String payload,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature
    ) {
        if (!service.verifySignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        service.accept(payload);
        return ResponseEntity.ok().build();
    }
}
