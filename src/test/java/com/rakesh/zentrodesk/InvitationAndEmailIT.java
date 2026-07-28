package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.common.exception.ResourceNotFoundException;
import com.rakesh.zentrodesk.iam.authentication.AccountType;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.refresh.RefreshTokenManager;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.platform.email.EmailDeliveryMessage;
import com.rakesh.zentrodesk.platform.email.EmailOutboxProcessor;
import com.rakesh.zentrodesk.platform.email.LocalEmailDeliveryGateway;
import com.rakesh.zentrodesk.platform.invitation.InvitationAcceptRequest;
import com.rakesh.zentrodesk.platform.invitation.InvitationDetailsResponse;
import com.rakesh.zentrodesk.platform.invitation.InvitationResponse;
import com.rakesh.zentrodesk.platform.invitation.InvitationService;
import com.rakesh.zentrodesk.platform.invitation.InvitationStatus;
import com.rakesh.zentrodesk.platform.invitation.InvitationTokenRequest;
import com.rakesh.zentrodesk.platform.password.PasswordResetCompleteRequest;
import com.rakesh.zentrodesk.platform.password.PasswordResetService;
import com.rakesh.zentrodesk.platform.password.PasswordResetStartRequest;
import com.rakesh.zentrodesk.platform.user.UserCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InvitationAndEmailIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private EmailOutboxProcessor emailOutboxProcessor;

    @Autowired
    private LocalEmailDeliveryGateway localEmailDeliveryGateway;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenManager refreshTokenManager;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearCapturedEmail() {
        localEmailDeliveryGateway.clear();
    }

    @AfterEach
    void clearContexts() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
        localEmailDeliveryGateway.clear();
    }

    @Test
    void inviteIsHashedDeliveredAcceptedOnceAndAudited() {
        Fixture fixture = createFixture("accept");
        authenticate(fixture.tenantId(), fixture.actorId());
        String email = uniqueEmail("invite");

        InvitationResponse invitation = invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Invited", "Owner", email, "9000000000")
        );

        assertFalse(userActive(invitation.userId()));
        emailOutboxProcessor.deliverPendingNow();
        String token = capturedToken(email);
        String storedHash = jdbcClient.sql("""
                        select token_hash from zentro_desk.tenant_invitations where id = :id
                        """)
                .param("id", invitation.id())
                .query(String.class)
                .single();
        assertNotEquals(token, storedHash);
        assertEquals(64, storedHash.length());
        String ciphertext = jdbcClient.sql("""
                        select token_ciphertext from zentro_desk.email_outbox_messages where reference_id = :id
                        """)
                .param("id", invitation.id())
                .query(String.class)
                .single();
        assertFalse(ciphertext.contains(token));

        InvitationDetailsResponse details = invitationService.validate(new InvitationTokenRequest(token));
        assertEquals(email, details.email());
        invitationService.accept(new InvitationAcceptRequest(token, "AcceptedPass1!"));

        assertTrue(userActive(invitation.userId()));
        String passwordHash = userPasswordHash(invitation.userId());
        assertTrue(passwordEncoder.matches("AcceptedPass1!", passwordHash));
        assertThrows(BadRequestException.class,
                () -> invitationService.accept(new InvitationAcceptRequest(token, "AnotherPass1!")));
        assertThrows(BadRequestException.class, () -> invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Duplicate", null, email, null)
        ));
        Integer auditCount = jdbcClient.sql("""
                        select count(*) from zentro_desk.audit_events
                        where tenant_id = :tenantId and entity_type = 'INVITATION'
                          and action in ('CREATE', 'ACCEPT')
                        """)
                .param("tenantId", fixture.tenantId())
                .query(Integer.class)
                .single();
        assertEquals(2, auditCount);
    }

    @Test
    void resendRevokesOldTokenAndTenantBoundaryProtectsRevocation() {
        Fixture tenantA = createFixture("resend-a");
        Fixture tenantB = createFixture("resend-b");
        authenticate(tenantA.tenantId(), tenantA.actorId());
        String email = uniqueEmail("resend");
        InvitationResponse first = invitationService.invite(
                new UserCreateRequest(tenantA.roleId(), "Resend", null, email, null)
        );
        emailOutboxProcessor.deliverPendingNow();
        String firstToken = capturedToken(email);
        localEmailDeliveryGateway.clear();

        InvitationResponse replacement = invitationService.resend(first.id());
        emailOutboxProcessor.deliverPendingNow();
        String replacementToken = capturedToken(email);
        assertNotEquals(firstToken, replacementToken);
        assertThrows(BadRequestException.class,
                () -> invitationService.validate(new InvitationTokenRequest(firstToken)));

        authenticate(tenantB.tenantId(), tenantB.actorId());
        assertThrows(ResourceNotFoundException.class, () -> invitationService.revoke(replacement.id()));

        authenticate(tenantA.tenantId(), tenantA.actorId());
        invitationService.revoke(replacement.id());
        assertThrows(BadRequestException.class,
                () -> invitationService.validate(new InvitationTokenRequest(replacementToken)));

        InvitationResponse expiring = invitationService.invite(
                new UserCreateRequest(tenantA.roleId(), "Expired", null, uniqueEmail("expired"), null)
        );
        jdbcClient.sql("""
                        update zentro_desk.tenant_invitations
                        set expires_at = current_timestamp - interval '1 second'
                        where id = :id
                        """)
                .param("id", expiring.id())
                .update();
        emailOutboxProcessor.deliverPendingNow();
        String expiredToken = localEmailDeliveryGateway.capturedMessages().stream()
                .filter(message -> message.recipient().equals(expiring.email()))
                .map(this::tokenFrom)
                .findFirst()
                .orElseThrow();
        assertThrows(BadRequestException.class,
                () -> invitationService.validate(new InvitationTokenRequest(expiredToken)));
        InvitationResponse renewed = invitationService.resend(expiring.id());
        assertEquals(InvitationStatus.PENDING, renewed.status());
    }

    @Test
    void invitationsEnforceSeatsAndExpiredInvitationsReleaseCapacity() {
        Fixture fixture = createFixture("seats");
        authenticate(fixture.tenantId(), fixture.actorId());
        jdbcClient.sql("update zentro_desk.tenants set seat_limit = 1 where id = :tenantId")
                .param("tenantId", fixture.tenantId())
                .update();

        InvitationResponse first = invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "First", null, uniqueEmail("seat-first"), null)
        );
        assertThrows(BadRequestException.class, () -> invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Second", null, uniqueEmail("seat-second"), null)
        ));

        jdbcClient.sql("""
                        update zentro_desk.tenant_invitations
                        set expires_at = current_timestamp - interval '1 second'
                        where id = :id
                        """)
                .param("id", first.id())
                .update();

        InvitationResponse replacement = invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Replacement", null, uniqueEmail("seat-replacement"), null)
        );
        assertEquals("PENDING", replacement.status().name());
    }

    @Test
    void invitationListingFiltersBySearchRoleAndDerivedStatus() {
        Fixture fixture = createFixture("filters");
        authenticate(fixture.tenantId(), fixture.actorId());
        String matchingEmail = uniqueEmail("matching");
        InvitationResponse matching = invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Matching", "Invite", matchingEmail, null)
        );
        invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Other", "Invite", uniqueEmail("other"), null)
        );

        var pending = invitationService.findAll(
                0, 20, "createdAt", "DESC", matchingEmail, fixture.roleId(), InvitationStatus.PENDING
        );
        assertEquals(List.of(matching.id()), pending.content().stream().map(InvitationResponse::id).toList());

        jdbcClient.sql("""
                        update zentro_desk.tenant_invitations
                        set expires_at = current_timestamp - interval '1 minute'
                        where id = :id
                        """)
                .param("id", matching.id())
                .update();

        var expired = invitationService.findAll(
                0, 20, "createdAt", "DESC", matchingEmail, fixture.roleId(), InvitationStatus.EXPIRED
        );
        assertEquals(List.of(matching.id()), expired.content().stream().map(InvitationResponse::id).toList());
    }

    @Test
    void passwordResetIsNonEnumeratingOneTimeAndRevokesSessions() {
        Fixture fixture = createFixture("reset");
        UUID userId = insertActiveUser(fixture, uniqueEmail("reset-user"), "OldPassword1!");
        String email = userEmail(userId);

        passwordResetService.request(new PasswordResetStartRequest(uniqueEmail("missing"), fixture.tenantCode()));
        assertEquals(0, resetTokenCount(fixture.tenantId()));

        passwordResetService.request(new PasswordResetStartRequest(email, fixture.tenantCode()));
        emailOutboxProcessor.deliverPendingNow();
        String token = capturedToken(email);
        RefreshTokenManager.IssuedRefreshToken refreshToken = refreshTokenManager.issue(
                AccountType.TENANT,
                userId,
                Instant.now().plusSeconds(3600)
        );

        passwordResetService.complete(new PasswordResetCompleteRequest(token, "NewPassword1!"));

        assertTrue(passwordEncoder.matches("NewPassword1!", userPasswordHash(userId)));
        assertEquals(1, userSessionVersion(userId));
        assertThrows(BadRequestException.class,
                () -> passwordResetService.complete(new PasswordResetCompleteRequest(token, "ReplayPass1!")));
        Integer activeRefreshTokens = jdbcClient.sql("""
                        select count(*) from zentro_desk.account_refresh_tokens
                        where account_id = :userId and revoked = false
                        """)
                .param("userId", userId)
                .query(Integer.class)
                .single();
        assertEquals(0, activeRefreshTokens);
        assertFalse(refreshToken.token().isBlank());

        jdbcClient.sql("update zentro_desk.tenant_users set active = false where id = :id")
                .param("id", userId)
                .update();
        int before = resetTokenCount(fixture.tenantId());
        passwordResetService.request(new PasswordResetStartRequest(email, fixture.tenantCode()));
        assertEquals(before, resetTokenCount(fixture.tenantId()));
    }

    @Test
    void corruptOutboxPayloadUsesBoundedRetriesWithoutLeakingToken() {
        Fixture fixture = createFixture("retry");
        authenticate(fixture.tenantId(), fixture.actorId());
        InvitationResponse invitation = invitationService.invite(
                new UserCreateRequest(fixture.roleId(), "Retry", null, uniqueEmail("retry"), null)
        );
        UUID outboxId = jdbcClient.sql("""
                        select id from zentro_desk.email_outbox_messages where reference_id = :referenceId
                        """)
                .param("referenceId", invitation.id())
                .query(UUID.class)
                .single();
        jdbcClient.sql("""
                        update zentro_desk.email_outbox_messages
                        set token_ciphertext = 'invalid-ciphertext'
                        where id = :id
                        """)
                .param("id", outboxId)
                .update();

        for (int attempt = 0; attempt < 5; attempt++) {
            jdbcClient.sql("""
                            update zentro_desk.email_outbox_messages
                            set next_attempt_at = current_timestamp where id = :id
                            """)
                    .param("id", outboxId)
                    .update();
            emailOutboxProcessor.deliverPendingNow();
        }

        String status = jdbcClient.sql("select status from zentro_desk.email_outbox_messages where id = :id")
                .param("id", outboxId)
                .query(String.class)
                .single();
        Integer attempts = jdbcClient.sql("select attempts from zentro_desk.email_outbox_messages where id = :id")
                .param("id", outboxId)
                .query(Integer.class)
                .single();
        String error = jdbcClient.sql("select last_error from zentro_desk.email_outbox_messages where id = :id")
                .param("id", outboxId)
                .query(String.class)
                .single();
        assertEquals("FAILED", status);
        assertEquals(5, attempts);
        assertFalse(error.contains("token="));
    }

    @Test
    void publicRecoveryEndpointsDoNotRequireAuthenticationOrEnumerateAccounts() throws Exception {
        mockMvc.perform(post("/api/app/auth/password-reset/request")
                        .contentType("application/json")
                        .content("""
                                {"email":"missing@example.test","tenantCode":"unknown"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "If the account is eligible, password reset instructions will be sent"));

        mockMvc.perform(post("/api/app/auth/invitations/validate")
                        .contentType("application/json")
                        .content("""
                                {"token":"invalid-token"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private Fixture createFixture(String prefix) {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String tenantCode = prefix + "-" + tenantId.toString().substring(0, 8);
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, :name, :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("name", tenantCode)
                .param("code", tenantCode)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_roles
                            (id, created_at, updated_at, tenant_id, name, template_code, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, 'Owner', 'OWNER', true)
                        """)
                .param("id", roleId)
                .param("tenantId", tenantId)
                .update();
        return new Fixture(tenantId, roleId, actorId, tenantCode);
    }

    private UUID insertActiveUser(Fixture fixture, String email, String password) {
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email,
                             password_hash, session_version, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId,
                                'Reset', :email, :passwordHash, 0, true)
                        """)
                .param("id", userId)
                .param("tenantId", fixture.tenantId())
                .param("roleId", fixture.roleId())
                .param("email", email)
                .param("passwordHash", passwordEncoder.encode(password))
                .update();
        return userId;
    }

    private void authenticate(UUID tenantId, UUID actorId) {
        tenantContext.setTenantId(tenantId);
        TenantPrincipal principal = new TenantPrincipal(actorId, tenantId, "owner@example.com", "", "Owner", null,
                UUID.randomUUID(), "Owner", true, 0, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private String capturedToken(String recipient) {
        return localEmailDeliveryGateway.capturedMessages().stream()
                .filter(message -> message.recipient().equals(recipient))
                .map(this::tokenFrom)
                .findFirst()
                .orElseThrow();
    }

    private String tokenFrom(EmailDeliveryMessage message) {
        return UriComponentsBuilder.fromUriString(message.actionUrl()).build().getQueryParams().getFirst("token");
    }

    private boolean userActive(UUID userId) {
        return jdbcClient.sql("select active from zentro_desk.tenant_users where id = :id")
                .param("id", userId)
                .query(Boolean.class)
                .single();
    }

    private String userPasswordHash(UUID userId) {
        return jdbcClient.sql("select password_hash from zentro_desk.tenant_users where id = :id")
                .param("id", userId)
                .query(String.class)
                .single();
    }

    private String userEmail(UUID userId) {
        return jdbcClient.sql("select email from zentro_desk.tenant_users where id = :id")
                .param("id", userId)
                .query(String.class)
                .single();
    }

    private int userSessionVersion(UUID userId) {
        return jdbcClient.sql("select session_version from zentro_desk.tenant_users where id = :id")
                .param("id", userId)
                .query(Integer.class)
                .single();
    }

    private int resetTokenCount(UUID tenantId) {
        return jdbcClient.sql("select count(*) from zentro_desk.password_reset_tokens where tenant_id = :tenantId")
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    private record Fixture(UUID tenantId, UUID roleId, UUID actorId, String tenantCode) {
    }
}
