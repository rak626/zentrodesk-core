package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.communication.notification.api.NotificationService;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfiguration;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfigurationRepository;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionMethod;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppCredentialAccess;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppFundingStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppOutboxAccess;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppPhoneNumber;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppTokenProfile;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppConfigurationServiceTest {

    private final WhatsAppConfigurationRepository repository = mock(WhatsAppConfigurationRepository.class);
    private final WhatsAppCredentialAccess credentialAccess = mock(WhatsAppCredentialAccess.class);
    private final WhatsAppOutboxAccess outboxAccess = mock(WhatsAppOutboxAccess.class);
    private final CurrentTenant currentTenant = mock(CurrentTenant.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final WhatsAppConfigurationService service = new WhatsAppConfigurationService(
            repository,
            credentialAccess,
            outboxAccess,
            currentTenant,
            userRepository,
            notificationService
    );
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appId", "zentrodesk-meta-app");
        ReflectionTestUtils.setField(service, "embeddedSignupConfigurationId", "embedded-config");
        ReflectionTestUtils.setField(service, "graphApiVersion", "v23.0");
        when(currentTenant.id()).thenReturn(tenantId);
    }

    @Test
    void manualConnectionValidatesAndStoresOnlyEncryptedToken() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(credentialAccess.tokenProfile("tenant-business-token")).thenReturn(new WhatsAppTokenProfile(
                true,
                "zentrodesk-meta-app",
                Set.of("whatsapp_business_messaging", "whatsapp_business_management"),
                expiresAt
        ));
        when(credentialAccess.businessOwnsWaba(
                "tenant-business", "tenant-waba", "tenant-business-token"
        )).thenReturn(true);
        when(credentialAccess.phoneNumbers("tenant-waba", "tenant-business-token")).thenReturn(
                java.util.List.of(new WhatsAppPhoneNumber(
                        "tenant-phone", "+91 90000 00000", "Tenant Travel", "GREEN"
                ))
        );
        when(credentialAccess.wabaIsFunded("tenant-waba", "tenant-business-token")).thenReturn(true);
        when(credentialAccess.encrypt("tenant-business-token")).thenReturn("aes-gcm-ciphertext");
        when(repository.saveAndFlush(any(WhatsAppConfiguration.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        WhatsAppConfigurationResponse response = service.save(new WhatsAppConfigurationRequest(
                "tenant-waba",
                "tenant-business",
                "tenant-phone",
                "tenant-business-token",
                "123456"
        ));

        assertEquals(WhatsAppConnectionStatus.CONNECTED, response.status());
        assertEquals(WhatsAppConnectionMethod.MANUAL, response.connectionMethod());
        assertEquals(WhatsAppFundingStatus.FUNDED, response.fundingStatus());
        assertTrue(response.tokenHealthy());
        verify(credentialAccess).register("tenant-phone", "123456", "tenant-business-token");
        verify(credentialAccess).subscribe("tenant-waba", "tenant-business-token");
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(configuration ->
                "aes-gcm-ciphertext".equals(configuration.getAccessTokenCiphertext())
                        && !"tenant-business-token".equals(configuration.getAccessTokenCiphertext())
        ));
    }

    @Test
    void rejectsTokenIssuedForAnotherMetaAppBeforeRegistration() {
        when(credentialAccess.tokenProfile("foreign-token")).thenReturn(new WhatsAppTokenProfile(
                true,
                "foreign-app",
                Set.of("whatsapp_business_messaging", "whatsapp_business_management"),
                null
        ));

        assertThrows(BadRequestException.class, () -> service.save(new WhatsAppConfigurationRequest(
                "tenant-waba",
                "tenant-business",
                "tenant-phone",
                "foreign-token",
                "123456"
        )));

        verify(credentialAccess, never()).register(any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void embeddedSignupExchangesCodeServerSide() {
        when(credentialAccess.exchangeAuthorizationCode("short-lived-code"))
                .thenReturn("tenant-business-token");
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(credentialAccess.tokenProfile("tenant-business-token")).thenReturn(new WhatsAppTokenProfile(
                true,
                "zentrodesk-meta-app",
                Set.of("whatsapp_business_messaging", "whatsapp_business_management"),
                null
        ));
        when(credentialAccess.businessOwnsWaba(any(), any(), any())).thenReturn(true);
        when(credentialAccess.phoneNumbers(any(), any())).thenReturn(java.util.List.of(
                new WhatsAppPhoneNumber("tenant-phone", "+91 90000 00000", "Tenant Travel", "GREEN")
        ));
        when(credentialAccess.wabaIsFunded(any(), any())).thenReturn(true);
        when(credentialAccess.encrypt(any())).thenReturn("aes-gcm-ciphertext");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WhatsAppConfigurationResponse response = service.completeEmbeddedSignup(
                new WhatsAppEmbeddedSignupRequest(
                        "short-lived-code",
                        "tenant-waba",
                        "tenant-business",
                        "tenant-phone",
                        "123456"
                )
        );

        assertEquals(WhatsAppConnectionMethod.EMBEDDED_SIGNUP, response.connectionMethod());
        verify(credentialAccess).exchangeAuthorizationCode("short-lived-code");
    }

    @Test
    void disconnectClearsTokenAndFailsPendingOldSenderMessages() {
        WhatsAppConfiguration configuration = WhatsAppConfiguration.builder()
                .tenantId(tenantId)
                .wabaId("tenant-waba")
                .businessId("tenant-business")
                .phoneNumberId("tenant-phone")
                .displayPhoneNumber("+91 90000 00000")
                .connectionMethod(WhatsAppConnectionMethod.MANUAL)
                .fundingStatus(WhatsAppFundingStatus.FUNDED)
                .accessTokenCiphertext("aes-gcm-ciphertext")
                .tokenHealthy(true)
                .status(WhatsAppConnectionStatus.CONNECTED)
                .build();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(configuration));

        WhatsAppConfigurationResponse response = service.disconnect();

        assertEquals(WhatsAppConnectionStatus.DISCONNECTED, response.status());
        assertEquals(null, configuration.getAccessTokenCiphertext());
        verify(outboxAccess).failPending(
                tenantId,
                "tenant-phone",
                "WhatsApp sender was disconnected"
        );
    }
}
