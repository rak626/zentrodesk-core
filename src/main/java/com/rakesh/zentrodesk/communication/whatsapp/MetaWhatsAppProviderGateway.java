package com.rakesh.zentrodesk.communication.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
class MetaWhatsAppProviderGateway implements WhatsAppProviderGateway {

    private final URI graphBaseUri;
    private final String graphVersion;
    private final String appId;
    private final String appSecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    MetaWhatsAppProviderGateway(
            @Value("${app.whatsapp.graph-base-url:https://graph.facebook.com}") URI graphBaseUri,
            @Value("${app.whatsapp.graph-version}") String graphVersion,
            @Value("${app.whatsapp.app-id}") String appId,
            @Value("${app.whatsapp.app-secret}") String appSecret,
            ObjectMapper objectMapper
    ) {
        this.graphBaseUri = graphBaseUri;
        this.graphVersion = graphVersion;
        this.appId = appId;
        this.appSecret = appSecret;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public String exchangeAuthorizationCode(String code) {
        JsonNode root = executeWithoutBearer(
                "GET",
                uri("oauth/access_token?client_id=" + encode(appId)
                        + "&client_secret=" + encode(appSecret)
                        + "&code=" + encode(code)),
                null
        );
        String accessToken = root.path("access_token").asText();
        if (accessToken.isBlank()) {
            throw new WhatsAppDeliveryException("Meta did not return a business access token", false);
        }
        return accessToken;
    }

    @Override
    public WhatsAppTokenProfile tokenProfile(String accessToken) {
        JsonNode data = executeWithoutBearer(
                "GET",
                uri("debug_token?input_token=" + encode(accessToken)
                        + "&access_token=" + encode(appId + "|" + appSecret)),
                null
        ).path("data");
        Set<String> scopes = new HashSet<>();
        data.path("scopes").forEach(scope -> scopes.add(scope.asText()));
        long expiresAt = data.path("expires_at").asLong(0);
        return new WhatsAppTokenProfile(
                data.path("is_valid").asBoolean(false),
                data.path("app_id").asText(null),
                Set.copyOf(scopes),
                expiresAt > 0 ? Instant.ofEpochSecond(expiresAt) : null
        );
    }

    @Override
    public boolean businessOwnsWaba(String businessId, String wabaId, String accessToken) {
        JsonNode root = execute(
                "GET",
                uri(businessId + "/owned_whatsapp_business_accounts?fields=id&limit=200"),
                accessToken,
                null
        );
        for (JsonNode item : root.path("data")) {
            if (wabaId.equals(item.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean wabaIsFunded(String wabaId, String accessToken) {
        JsonNode root = execute(
                "GET",
                uri(wabaId + "?fields=id,currency,account_review_status"),
                accessToken,
                null
        );
        String reviewStatus = root.path("account_review_status").asText("");
        return !root.path("currency").asText("").isBlank()
                && !"REJECTED".equalsIgnoreCase(reviewStatus)
                && !"DISABLED".equalsIgnoreCase(reviewStatus);
    }

    @Override
    public List<WhatsAppPhoneNumber> phoneNumbers(String wabaId, String accessToken) {
        JsonNode root = execute("GET", uri(wabaId + "/phone_numbers?fields=id,display_phone_number,verified_name,quality_rating"),
                accessToken, null);
        List<WhatsAppPhoneNumber> result = new ArrayList<>();
        root.path("data").forEach(node -> result.add(toPhoneNumber(node)));
        return List.copyOf(result);
    }

    @Override
    public WhatsAppPhoneNumber phoneNumber(String phoneNumberId, String accessToken) {
        JsonNode root = execute("GET", uri(phoneNumberId + "?fields=id,display_phone_number,verified_name,quality_rating"),
                accessToken, null);
        return toPhoneNumber(root);
    }

    @Override
    public void subscribe(String wabaId, String accessToken) {
        execute("POST", uri(wabaId + "/subscribed_apps"), accessToken, "{}");
    }

    @Override
    public void register(String phoneNumberId, String registrationPin, String accessToken) {
        try {
            execute(
                    "POST",
                    uri(phoneNumberId + "/register"),
                    accessToken,
                    objectMapper.writeValueAsString(Map.of(
                            "messaging_product", "whatsapp",
                            "pin", registrationPin
                    ))
            );
        } catch (JacksonException exception) {
            throw new WhatsAppDeliveryException("Unable to serialize WhatsApp registration", false, exception);
        }
    }

    @Override
    public WhatsAppDeliveryResult sendText(
            String phoneNumberId,
            String accessToken,
            String recipient,
            String message,
            UUID clientReference
    ) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", recipient,
                    "type", "text",
                    "biz_opaque_callback_data", clientReference.toString(),
                    "text", Map.of("preview_url", false, "body", message)
            ));
            JsonNode root = execute("POST", uri(phoneNumberId + "/messages"), accessToken, body);
            String providerMessageId = root.path("messages").path(0).path("id").asText();
            if (providerMessageId.isBlank()) {
                throw new WhatsAppDeliveryException("Meta response did not include a message ID", false);
            }
            return new WhatsAppDeliveryResult(providerMessageId);
        } catch (JacksonException exception) {
            throw new WhatsAppDeliveryException("Unable to serialize WhatsApp request", false, exception);
        }
    }

    @Override
    public WhatsAppDeliveryResult sendDocumentTemplate(
            String phoneNumberId,
            String accessToken,
            String recipient,
            byte[] document,
            String fileName,
            String templateName,
            String templateLanguage,
            UUID clientReference
    ) {
        try {
            String mediaId = uploadDocument(phoneNumberId, accessToken, document, fileName);
            Map<String, Object> documentParameter = Map.of(
                    "type", "document",
                    "document", Map.of("id", mediaId, "filename", fileName)
            );
            Map<String, Object> header = Map.of(
                    "type", "header",
                    "parameters", List.of(documentParameter)
            );
            String body = objectMapper.writeValueAsString(Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", recipient,
                    "type", "template",
                    "biz_opaque_callback_data", clientReference.toString(),
                    "template", Map.of(
                            "name", templateName,
                            "language", Map.of("code", templateLanguage),
                            "components", List.of(header)
                    )
            ));
            JsonNode root = execute("POST", uri(phoneNumberId + "/messages"), accessToken, body);
            String providerMessageId = root.path("messages").path(0).path("id").asText();
            if (providerMessageId.isBlank()) {
                throw new WhatsAppDeliveryException("Meta response did not include a message ID", false);
            }
            return new WhatsAppDeliveryResult(providerMessageId);
        } catch (JacksonException exception) {
            throw new WhatsAppDeliveryException("Unable to serialize WhatsApp document request", false, exception);
        }
    }

    private String uploadDocument(
            String phoneNumberId,
            String accessToken,
            byte[] document,
            String fileName
    ) {
        String boundary = "----ZentroDesk" + UUID.randomUUID().toString().replace("-", "");
        String safeFileName = fileName.replace("\"", "");
        byte[] prefix = (
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"messaging_product\"\r\n\r\n"
                        + "whatsapp\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName + "\"\r\n"
                        + "Content-Type: application/pdf\r\n\r\n"
        ).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        JsonNode response = executeMultipart(
                uri(phoneNumberId + "/media"),
                accessToken,
                boundary,
                List.of(prefix, document, suffix)
        );
        String mediaId = response.path("id").asText();
        if (mediaId.isBlank()) {
            throw new WhatsAppDeliveryException("Meta response did not include a media ID", false);
        }
        return mediaId;
    }

    private JsonNode execute(String method, URI uri, String accessToken, String body) {
        return execute(method, uri, accessToken, body, true);
    }

    private JsonNode executeWithoutBearer(String method, URI uri, String body) {
        return execute(method, uri, null, body, false);
    }

    private JsonNode executeMultipart(
            URI uri,
            String accessToken,
            String boundary,
            List<byte[]> bodyParts
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(bodyParts))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                throw new WhatsAppDeliveryException(
                        "Meta WhatsApp media API returned HTTP " + response.statusCode(),
                        retryable
                );
            }
            return objectMapper.readTree(response.body());
        } catch (WhatsAppDeliveryException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new WhatsAppDeliveryException("Unable to parse Meta WhatsApp media response", false, exception);
        } catch (IOException exception) {
            throw new WhatsAppDeliveryException("Meta WhatsApp media upload failed", true, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WhatsAppDeliveryException("Meta WhatsApp media upload was interrupted", true, exception);
        }
    }

    private JsonNode execute(String method, URI uri, String accessToken, String body, boolean bearerRequired) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            if (bearerRequired) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
            HttpRequest request = "POST".equals(method)
                    ? builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()
                    : builder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                throw new WhatsAppDeliveryException(
                        "Meta WhatsApp API returned HTTP " + response.statusCode(),
                        retryable
                );
            }
            return objectMapper.readTree(response.body());
        } catch (WhatsAppDeliveryException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new WhatsAppDeliveryException("Unable to parse Meta WhatsApp response", false, exception);
        } catch (IOException exception) {
            throw new WhatsAppDeliveryException("Meta WhatsApp connection failed", true, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WhatsAppDeliveryException("Meta WhatsApp request was interrupted", true, exception);
        }
    }

    private WhatsAppPhoneNumber toPhoneNumber(JsonNode node) {
        return new WhatsAppPhoneNumber(
                node.path("id").asText(),
                node.path("display_phone_number").asText(),
                node.path("verified_name").asText(null),
                node.path("quality_rating").asText(null)
        );
    }

    private URI uri(String path) {
        String base = graphBaseUri.toString().replaceAll("/$", "");
        return URI.create(base + "/" + graphVersion + "/" + path);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
