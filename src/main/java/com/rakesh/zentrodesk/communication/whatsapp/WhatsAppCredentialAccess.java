package com.rakesh.zentrodesk.communication.whatsapp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WhatsAppCredentialAccess {

    private final WhatsAppCredentialCipher credentialCipher;
    private final WhatsAppProviderGateway providerGateway;

    public String encrypt(String accessToken) {
        return credentialCipher.encrypt(accessToken);
    }

    public String decrypt(String ciphertext) {
        return credentialCipher.decrypt(ciphertext);
    }

    public String exchangeAuthorizationCode(String code) {
        return providerGateway.exchangeAuthorizationCode(code);
    }

    public WhatsAppTokenProfile tokenProfile(String accessToken) {
        return providerGateway.tokenProfile(accessToken);
    }

    public boolean businessOwnsWaba(String businessId, String wabaId, String accessToken) {
        return providerGateway.businessOwnsWaba(businessId, wabaId, accessToken);
    }

    public boolean wabaIsFunded(String wabaId, String accessToken) {
        return providerGateway.wabaIsFunded(wabaId, accessToken);
    }

    public List<WhatsAppPhoneNumber> phoneNumbers(String wabaId, String accessToken) {
        return providerGateway.phoneNumbers(wabaId, accessToken);
    }

    public WhatsAppPhoneNumber phoneNumber(String phoneNumberId, String accessToken) {
        return providerGateway.phoneNumber(phoneNumberId, accessToken);
    }

    public void subscribe(String wabaId, String accessToken) {
        providerGateway.subscribe(wabaId, accessToken);
    }

    public void register(String phoneNumberId, String registrationPin, String accessToken) {
        providerGateway.register(phoneNumberId, registrationPin, accessToken);
    }

    public WhatsAppDeliveryResult sendText(
            String phoneNumberId,
            String accessToken,
            String recipient,
            String message,
            UUID clientReference
    ) {
        return providerGateway.sendText(phoneNumberId, accessToken, recipient, message, clientReference);
    }
}
