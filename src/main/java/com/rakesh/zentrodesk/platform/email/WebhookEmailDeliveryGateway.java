package com.rakesh.zentrodesk.platform.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
@Profile({"staging", "prod"})
class WebhookEmailDeliveryGateway implements EmailDeliveryGateway {

    private final URI deliveryUri;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    WebhookEmailDeliveryGateway(
            @Value("${app.email.delivery-url}") URI deliveryUri,
            @Value("${app.email.delivery-api-key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.deliveryUri = deliveryUri;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public void deliver(EmailDeliveryMessage message) {
        try {
            HttpRequest request = HttpRequest.newBuilder(deliveryUri)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Email delivery returned HTTP " + response.statusCode());
            }
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize email delivery request", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Email delivery connection failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Email delivery was interrupted", exception);
        }
    }
}
