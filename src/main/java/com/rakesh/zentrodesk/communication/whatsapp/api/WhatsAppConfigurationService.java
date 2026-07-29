package com.rakesh.zentrodesk.communication.whatsapp.api;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ConflictException;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfiguration;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfigurationRepository;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionMethod;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppCredentialAccess;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppFundingStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppOutboxAccess;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppPhoneNumber;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppTokenProfile;
import com.rakesh.zentrodesk.communication.notification.api.NotificationService;
import com.rakesh.zentrodesk.platform.audit.AuditMutation;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WhatsAppConfigurationService {

    private static final Set<String> REQUIRED_SCOPES = Set.of(
            "whatsapp_business_messaging",
            "whatsapp_business_management"
    );

    private final WhatsAppConfigurationRepository repository;
    private final WhatsAppCredentialAccess credentialAccess;
    private final WhatsAppOutboxAccess outboxAccess;
    private final CurrentTenant currentTenant;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.whatsapp.app-id}")
    private String appId;

    @Value("${app.whatsapp.embedded-signup-config-id}")
    private String embeddedSignupConfigurationId;

    @Value("${app.whatsapp.graph-version}")
    private String graphApiVersion;

    @Transactional(readOnly = true)
    public WhatsAppConfigurationResponse get() {
        return repository.findByTenantId(currentTenant.id())
                .map(this::toResponse)
                .orElseGet(this::emptyResponse);
    }

    @Transactional(readOnly = true)
    public WhatsAppConnectionHealthResponse health() {
        return repository.findByTenantId(currentTenant.id())
                .map(this::toHealth)
                .orElseGet(() -> new WhatsAppConnectionHealthResponse(
                        false, null, WhatsAppFundingStatus.UNKNOWN, false,
                        null, null, null, null, "WhatsApp is not connected"
                ));
    }

    public WhatsAppEmbeddedSignupConfigurationResponse embeddedSignupConfiguration() {
        return new WhatsAppEmbeddedSignupConfigurationResponse(
                appId,
                embeddedSignupConfigurationId,
                graphApiVersion
        );
    }

    @Transactional(readOnly = true)
    public List<WhatsAppPhoneNumber> discover(WhatsAppDiscoveryRequest request) {
        String token = request.accessToken().trim();
        validateToken(token);
        try {
            return credentialAccess.phoneNumbers(request.wabaId().trim(), token);
        } catch (RuntimeException exception) {
            throw new BadRequestException("Unable to load phone numbers from Meta");
        }
    }

    @Transactional
    @AuditMutation(entityType = "WHATSAPP_CONFIGURATION", action = "CONNECT_EMBEDDED")
    public WhatsAppConfigurationResponse completeEmbeddedSignup(WhatsAppEmbeddedSignupRequest request) {
        String accessToken;
        try {
            accessToken = credentialAccess.exchangeAuthorizationCode(request.authorizationCode().trim());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Embedded Signup authorization code exchange failed");
        }
        return connect(
                request.wabaId(),
                request.businessId(),
                request.phoneNumberId(),
                request.registrationPin(),
                accessToken,
                WhatsAppConnectionMethod.EMBEDDED_SIGNUP
        );
    }

    @Transactional
    @AuditMutation(entityType = "WHATSAPP_CONFIGURATION", action = "CONNECT_MANUAL")
    public WhatsAppConfigurationResponse save(WhatsAppConfigurationRequest request) {
        return connect(
                request.wabaId(),
                request.businessId(),
                request.phoneNumberId(),
                request.registrationPin(),
                request.accessToken(),
                WhatsAppConnectionMethod.MANUAL
        );
    }

    @Transactional
    @AuditMutation(entityType = "WHATSAPP_CONFIGURATION", action = "VALIDATE")
    public WhatsAppConfigurationResponse validate() {
        WhatsAppConfiguration configuration = repository.findByTenantId(currentTenant.id())
                .orElseThrow(() -> new BadRequestException("WhatsApp is not configured"));
        if (configuration.getStatus() == WhatsAppConnectionStatus.DISCONNECTED
                || configuration.getAccessTokenCiphertext() == null) {
            throw new BadRequestException("WhatsApp is disconnected");
        }
        Instant now = Instant.now();
        try {
            String accessToken = credentialAccess.decrypt(configuration.getAccessTokenCiphertext());
            ValidatedConnection validated = validateConnection(
                    configuration.getBusinessId(),
                    configuration.getWabaId(),
                    configuration.getPhoneNumberId(),
                    accessToken,
                    null,
                    false
            );
            applyHealthy(configuration, validated, now);
        } catch (RuntimeException exception) {
            configuration.setFundingStatus(WhatsAppFundingStatus.UNKNOWN);
            applyError(configuration, now, "Meta connection validation failed");
        }
        return toResponse(configuration);
    }

    @Transactional
    @AuditMutation(entityType = "WHATSAPP_CONFIGURATION", action = "DISCONNECT")
    public WhatsAppConfigurationResponse disconnect() {
        WhatsAppConfiguration configuration = repository.findByTenantId(currentTenant.id())
                .orElseThrow(() -> new BadRequestException("WhatsApp is not configured"));
        outboxAccess.failPending(
                configuration.getTenantId(),
                configuration.getPhoneNumberId(),
                "WhatsApp sender was disconnected"
        );
        configuration.setStatus(WhatsAppConnectionStatus.DISCONNECTED);
        configuration.setTokenHealthy(false);
        configuration.setAccessTokenCiphertext(null);
        configuration.setAccessTokenExpiresAt(null);
        configuration.setLastValidatedAt(Instant.now());
        configuration.setLastError("Disconnected by tenant owner");
        return toResponse(configuration);
    }

    @Scheduled(cron = "${app.whatsapp.health-check-cron:0 15 2 * * *}")
    @Transactional
    public void refreshConnectionHealth() {
        repository.findAll().stream()
                .filter(configuration -> configuration.getStatus() != WhatsAppConnectionStatus.DISCONNECTED)
                .forEach(this::refreshConnectionHealth);
    }

    private WhatsAppConfigurationResponse connect(
            String rawWabaId,
            String rawBusinessId,
            String rawPhoneNumberId,
            String rawRegistrationPin,
            String rawAccessToken,
            WhatsAppConnectionMethod method
    ) {
        UUID tenantId = currentTenant.id();
        String wabaId = rawWabaId.trim();
        String businessId = rawBusinessId.trim();
        String phoneNumberId = rawPhoneNumberId.trim();
        String registrationPin = rawRegistrationPin.trim();
        String accessToken = rawAccessToken.trim();
        rejectForeignIdentifiers(tenantId, wabaId, phoneNumberId);

        ValidatedConnection validated = validateConnection(
                businessId,
                wabaId,
                phoneNumberId,
                accessToken,
                registrationPin,
                true
        );
        WhatsAppConfiguration configuration = repository.findByTenantId(tenantId)
                .orElseGet(() -> WhatsAppConfiguration.builder().tenantId(tenantId).build());
        String previousPhoneNumberId = configuration.getPhoneNumberId();
        if (previousPhoneNumberId != null && !previousPhoneNumberId.equals(phoneNumberId)) {
            outboxAccess.failPending(tenantId, previousPhoneNumberId, "WhatsApp sender was replaced");
        }
        Instant now = Instant.now();
        configuration.setWabaId(wabaId);
        configuration.setBusinessId(businessId);
        configuration.setPhoneNumberId(phoneNumberId);
        configuration.setDisplayPhoneNumber(validated.phoneNumber().displayPhoneNumber());
        configuration.setVerifiedName(validated.phoneNumber().verifiedName());
        configuration.setConnectionMethod(method);
        configuration.setAccessTokenCiphertext(credentialAccess.encrypt(accessToken));
        configuration.setAccessTokenExpiresAt(validated.tokenProfile().expiresAt());
        applyHealthy(configuration, validated, now);
        try {
            return toResponse(repository.saveAndFlush(configuration));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("This WABA or phone number is already connected to another tenant");
        }
    }

    private void refreshConnectionHealth(WhatsAppConfiguration configuration) {
        Instant now = Instant.now();
        try {
            String accessToken = credentialAccess.decrypt(configuration.getAccessTokenCiphertext());
            ValidatedConnection validated = validateConnection(
                    configuration.getBusinessId(),
                    configuration.getWabaId(),
                    configuration.getPhoneNumberId(),
                    accessToken,
                    null,
                    false
            );
            applyHealthy(configuration, validated, now);
            notifyTokenExpiry(configuration, now);
        } catch (RuntimeException exception) {
            String reason = exception instanceof BadRequestException
                    ? exception.getMessage()
                    : "Meta connection validation failed";
            if (reason != null && reason.contains("funding")) {
                configuration.setFundingStatus(WhatsAppFundingStatus.UNFUNDED);
            } else {
                configuration.setFundingStatus(WhatsAppFundingStatus.UNKNOWN);
            }
            applyError(configuration, now, reason);
            String episode = configuration.getLastHealthyAt() == null
                    ? "initial"
                    : Long.toString(configuration.getLastHealthyAt().getEpochSecond());
            notifyOwners(
                    configuration,
                    "whatsapp-health-failure-" + configuration.getId() + "-" + episode,
                    "WhatsApp connection needs attention",
                    reason
            );
        }
    }

    private ValidatedConnection validateConnection(
            String businessId,
            String wabaId,
            String phoneNumberId,
            String accessToken,
            String registrationPin,
            boolean prepareConnection
    ) {
        WhatsAppTokenProfile profile = validateToken(accessToken);
        try {
            if (!credentialAccess.businessOwnsWaba(businessId, wabaId, accessToken)) {
                throw new BadRequestException("The submitted business does not own this WABA");
            }
            WhatsAppPhoneNumber phoneNumber = credentialAccess.phoneNumbers(wabaId, accessToken).stream()
                    .filter(item -> phoneNumberId.equals(item.id()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "The submitted phone number is not accessible through this WABA"
                    ));
            if (!credentialAccess.wabaIsFunded(wabaId, accessToken)) {
                throw new BadRequestException("The tenant WABA has no valid funding source");
            }
            if (prepareConnection) {
                credentialAccess.register(phoneNumberId, registrationPin, accessToken);
                credentialAccess.subscribe(wabaId, accessToken);
            }
            return new ValidatedConnection(profile, phoneNumber);
        } catch (BadRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BadRequestException("Meta could not validate the submitted WhatsApp account");
        }
    }

    private WhatsAppTokenProfile validateToken(String accessToken) {
        WhatsAppTokenProfile profile;
        try {
            profile = credentialAccess.tokenProfile(accessToken);
        } catch (RuntimeException exception) {
            throw new BadRequestException("Meta could not validate the submitted business token");
        }
        if (!profile.valid() || !appId.equals(profile.appId())) {
            throw new BadRequestException("The business token does not belong to the ZentroDesk Meta App");
        }
        if (!profile.scopes().containsAll(REQUIRED_SCOPES)) {
            throw new BadRequestException("The business token lacks required WhatsApp permissions");
        }
        if (profile.expiresAt() != null && !profile.expiresAt().isAfter(Instant.now())) {
            throw new BadRequestException("The business token has expired");
        }
        return profile;
    }

    private void rejectForeignIdentifiers(UUID tenantId, String wabaId, String phoneNumberId) {
        if (repository.existsByWabaIdAndTenantIdNot(wabaId, tenantId)) {
            throw new ConflictException("This WhatsApp Business Account is already connected to another tenant");
        }
        if (repository.existsByPhoneNumberIdAndTenantIdNot(phoneNumberId, tenantId)) {
            throw new ConflictException("This WhatsApp phone number is already connected to another tenant");
        }
    }

    private void applyHealthy(
            WhatsAppConfiguration configuration,
            ValidatedConnection validated,
            Instant now
    ) {
        configuration.setDisplayPhoneNumber(validated.phoneNumber().displayPhoneNumber());
        configuration.setVerifiedName(validated.phoneNumber().verifiedName());
        configuration.setAccessTokenExpiresAt(validated.tokenProfile().expiresAt());
        configuration.setFundingStatus(WhatsAppFundingStatus.FUNDED);
        configuration.setTokenHealthy(true);
        configuration.setStatus(WhatsAppConnectionStatus.CONNECTED);
        configuration.setLastValidatedAt(now);
        configuration.setLastHealthyAt(now);
        configuration.setLastError(null);
    }

    private void applyError(WhatsAppConfiguration configuration, Instant now, String message) {
        configuration.setStatus(WhatsAppConnectionStatus.ERROR);
        configuration.setTokenHealthy(false);
        configuration.setLastValidatedAt(now);
        configuration.setLastError(message);
    }

    private void notifyTokenExpiry(WhatsAppConfiguration configuration, Instant now) {
        Instant expiresAt = configuration.getAccessTokenExpiresAt();
        if (expiresAt == null) {
            return;
        }
        long days = Math.max(0, Duration.between(now, expiresAt).toDays());
        int threshold = days <= 1 ? 1 : days <= 7 ? 7 : days <= 30 ? 30 : 0;
        if (threshold == 0) {
            return;
        }
        notifyOwners(
                configuration,
                "whatsapp-token-expiry-" + configuration.getId() + "-"
                        + expiresAt.getEpochSecond() + "-" + threshold,
                "WhatsApp token expires soon",
                "Reconnect the tenant WhatsApp account within " + threshold + " day(s)."
        );
    }

    private void notifyOwners(
            WhatsAppConfiguration configuration,
            String eventKey,
            String title,
            String message
    ) {
        userRepository.findActiveOwnerIds(configuration.getTenantId()).forEach(ownerId ->
                notificationService.createIdempotent(
                        configuration.getTenantId(),
                        ownerId,
                        eventKey,
                        "WHATSAPP",
                        title,
                        message,
                        "/settings/whatsapp",
                        "WHATSAPP_CONFIGURATION",
                        configuration.getId()
                )
        );
    }

    private WhatsAppConfigurationResponse emptyResponse() {
        return new WhatsAppConfigurationResponse(
                false, null, null, null, null, null, null,
                WhatsAppFundingStatus.UNKNOWN, false, null, null, null, null, null
        );
    }

    private WhatsAppConfigurationResponse toResponse(WhatsAppConfiguration configuration) {
        return new WhatsAppConfigurationResponse(
                true,
                configuration.getWabaId(),
                configuration.getBusinessId(),
                configuration.getPhoneNumberId(),
                configuration.getDisplayPhoneNumber(),
                configuration.getVerifiedName(),
                configuration.getConnectionMethod(),
                configuration.getFundingStatus(),
                configuration.isTokenHealthy(),
                configuration.getStatus(),
                configuration.getAccessTokenExpiresAt(),
                configuration.getLastValidatedAt(),
                configuration.getLastHealthyAt(),
                configuration.getLastError()
        );
    }

    private WhatsAppConnectionHealthResponse toHealth(WhatsAppConfiguration configuration) {
        boolean available = configuration.getStatus() == WhatsAppConnectionStatus.CONNECTED
                && configuration.getFundingStatus() == WhatsAppFundingStatus.FUNDED
                && configuration.isTokenHealthy()
                && (configuration.getAccessTokenExpiresAt() == null
                || configuration.getAccessTokenExpiresAt().isAfter(Instant.now()));
        return new WhatsAppConnectionHealthResponse(
                available,
                configuration.getStatus(),
                configuration.getFundingStatus(),
                configuration.isTokenHealthy(),
                configuration.getDisplayPhoneNumber(),
                configuration.getVerifiedName(),
                configuration.getAccessTokenExpiresAt(),
                configuration.getLastValidatedAt(),
                configuration.getLastError()
        );
    }

    private record ValidatedConnection(
            WhatsAppTokenProfile tokenProfile,
            WhatsAppPhoneNumber phoneNumber
    ) {
    }
}
