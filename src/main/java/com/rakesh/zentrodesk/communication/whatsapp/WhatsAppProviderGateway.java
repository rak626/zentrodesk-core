package com.rakesh.zentrodesk.communication.whatsapp;

import java.util.List;
import java.util.UUID;

interface WhatsAppProviderGateway {

    String exchangeAuthorizationCode(String code);

    WhatsAppTokenProfile tokenProfile(String accessToken);

    boolean businessOwnsWaba(String businessId, String wabaId, String accessToken);

    boolean wabaIsFunded(String wabaId, String accessToken);

    List<WhatsAppPhoneNumber> phoneNumbers(String wabaId, String accessToken);

    WhatsAppPhoneNumber phoneNumber(String phoneNumberId, String accessToken);

    void subscribe(String wabaId, String accessToken);

    void register(String phoneNumberId, String registrationPin, String accessToken);

    WhatsAppDeliveryResult sendText(
            String phoneNumberId,
            String accessToken,
            String recipient,
            String message,
            UUID clientReference
    );

    WhatsAppDeliveryResult sendDocumentTemplate(
            String phoneNumberId,
            String accessToken,
            String recipient,
            byte[] document,
            String fileName,
            String templateName,
            String templateLanguage,
            UUID clientReference
    );
}
