package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.communication.notification.NotificationStatus;
import com.rakesh.zentrodesk.communication.notification.api.NotificationResponse;
import com.rakesh.zentrodesk.communication.notification.api.NotificationService;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationCenterIT extends PostgreSqlIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("92000000-0000-0000-0000-000000000101");
    private static final UUID USER_ID = UUID.fromString("92000000-0000-0000-0000-000000000201");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TenantContext tenantContext;

    @BeforeAll
    void createFixture() {
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, 'Notification Tenant', 'NOTIFY-IT',
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", TENANT_ID)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_roles
                            (id, created_at, updated_at, tenant_id, name, template_code, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, 'Owner', 'OWNER', true)
                        """)
                .param("id", ROLE_ID)
                .param("tenantId", TENANT_ID)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email, password_hash, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId,
                                'Notification', 'notification-it@example.com', 'unused', true)
                        """)
                .param("id", USER_ID)
                .param("tenantId", TENANT_ID)
                .param("roleId", ROLE_ID)
                .update();
    }

    @BeforeEach
    void authenticate() {
        tenantContext.setTenantId(TENANT_ID);
        TenantPrincipal principal = principal();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsIdempotencyUnreadCountsSafeLinksAndReadCommands() {
        UUID sourceId = UUID.randomUUID();
        notificationService.createIdempotent(
                TENANT_ID, USER_ID, "PAYMENT:" + sourceId, "PAYMENT", "Payment recorded",
                "Payment received.", "/bookings?booking=" + sourceId, "PAYMENT", sourceId
        );
        notificationService.createIdempotent(
                TENANT_ID, USER_ID, "PAYMENT:" + sourceId, "PAYMENT", "Payment recorded",
                "Payment received.", "/bookings?booking=" + sourceId, "PAYMENT", sourceId
        );
        notificationService.createIdempotent(
                TENANT_ID, USER_ID, "SYSTEM:" + sourceId, "SYSTEM", "Unsafe link removed",
                "No external navigation.", "https://example.com", "SYSTEM", sourceId
        );

        TenantPrincipal user = principal();
        List<NotificationResponse> notifications = notificationService.findAll(
                0, 20, "createdAt", "DESC", null, null, user
        ).content();

        assertEquals(2, notifications.size());
        assertEquals(2, notificationService.unreadCount(user));
        assertNull(notifications.stream()
                .filter(notification -> "SYSTEM".equals(notification.category()))
                .findFirst()
                .orElseThrow()
                .deepLink());

        notificationService.markRead(notifications.getFirst().id(), user);
        assertEquals(1, notificationService.unreadCount(user));
        assertEquals(1, notificationService.markAllRead(user));
        assertEquals(0, notificationService.unreadCount(user));
        assertEquals(2, notificationService.findAll(
                0, 20, "createdAt", "DESC", NotificationStatus.READ, null, user
        ).content().size());
    }

    private TenantPrincipal principal() {
        return new TenantPrincipal(
                USER_ID,
                TENANT_ID,
                "notification-it@example.com",
                "",
                "Notification",
                "User",
                ROLE_ID,
                "Owner",
                true,
                0,
                List.of()
        );
    }
}
