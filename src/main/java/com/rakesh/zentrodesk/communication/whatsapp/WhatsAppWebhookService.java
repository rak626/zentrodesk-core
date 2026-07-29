package com.rakesh.zentrodesk.communication.whatsapp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    private final WhatsAppWebhookInboxRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${app.whatsapp.webhook-verify-token}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret}")
    private String appSecret;

    public boolean verifyChallenge(String mode, String token) {
        return "subscribe".equals(mode) && constantTimeEquals(verifyToken, token);
    }

    public boolean verifySignature(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );
            return constantTimeEquals(expected, signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify WhatsApp webhook", exception);
        }
    }

    @Transactional
    public void accept(String payload) {
        String hash = sha256(payload);
        if (repository.existsByPayloadHash(hash)) {
            return;
        }
        try {
            repository.save(WhatsAppWebhookInbox.builder()
                    .phoneNumberId(phoneNumberId(payload))
                    .payloadHash(hash)
                    .payload(payload)
                    .status(WhatsAppWebhookStatus.PENDING)
                    .nextAttemptAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent delivery of the same Meta webhook was already persisted.
        }
    }

    private String phoneNumberId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return root.path("entry").path(0).path("changes").path(0).path("value")
                    .path("metadata").path("phone_number_id").asText(null);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash WhatsApp webhook", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
