package com.rakesh.zentrodesk.communication.whatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppWebhookServiceTest {

    private static final String APP_SECRET = "shared-meta-app-secret";

    private final WhatsAppWebhookInboxRepository repository = mock(WhatsAppWebhookInboxRepository.class);
    private final WhatsAppWebhookService service = new WhatsAppWebhookService(repository, new ObjectMapper());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "verifyToken", "shared-verify-token");
        ReflectionTestUtils.setField(service, "appSecret", APP_SECRET);
    }

    @Test
    void verifiesChallengeAndSharedAppSignature() throws Exception {
        String payload = "{\"object\":\"whatsapp_business_account\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(service.verifyChallenge("subscribe", "shared-verify-token"));
        assertFalse(service.verifyChallenge("subscribe", "wrong-token"));
        assertTrue(service.verifySignature(payload, signature));
        assertFalse(service.verifySignature(payload, "sha256=invalid"));
    }

    @Test
    void persistsPhoneNumberRoutingSnapshotAndDeduplicatesPayload() {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {"metadata": {"phone_number_id": "tenant-phone"}}
                    }]
                  }]
                }
                """;

        service.accept(payload);

        verify(repository).save(argThat(webhook ->
                "tenant-phone".equals(webhook.getPhoneNumberId())
                        && webhook.getPayloadHash() != null
                        && webhook.getStatus() == WhatsAppWebhookStatus.PENDING
        ));

        when(repository.existsByPayloadHash(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        service.accept(payload);
        verify(repository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }
}
