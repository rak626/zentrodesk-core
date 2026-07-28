package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@AutoConfigureMockMvc
class FixedRbacIT extends PostgreSqlIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void enforcesRoleSpecificApiAccessAndFixedRoleImmutability() throws Exception {
        Fixture fixture = createFixture();
        String owner = login(fixture.tenantCode(), FixedTenantRole.OWNER);
        String manager = login(fixture.tenantCode(), FixedTenantRole.MANAGER);
        String sales = login(fixture.tenantCode(), FixedTenantRole.SALES);
        String operations = login(fixture.tenantCode(), FixedTenantRole.OPERATIONS);
        String accounts = login(fixture.tenantCode(), FixedTenantRole.ACCOUNTS);

        mockMvc.perform(get("/api/app/roles").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/app/roles/{id}", fixture.roles().get(FixedTenantRole.OWNER))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/app/users").header("Authorization", bearer(manager)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/app/audit-events").header("Authorization", bearer(manager)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/app/users/{id}", UUID.randomUUID()).header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/app/users/{id}/sessions/revoke", UUID.randomUUID())
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/app/leads").header("Authorization", bearer(sales)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/app/leads")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RBAC lead\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/app/properties").header("Authorization", bearer(sales)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/app/audit-events").header("Authorization", bearer(sales)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/app/properties").header("Authorization", bearer(operations)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/app/bookings/{id}/payments", UUID.randomUUID())
                        .header("Authorization", bearer(operations))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"method\":\"CASH\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/app/bookings").header("Authorization", bearer(accounts)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/app/leads").header("Authorization", bearer(accounts)))
                .andExpect(status().isForbidden());
    }

    @Test
    void persistsExactlyFiveTemplatesWithCanonicalPermissionSets() {
        Fixture fixture = createFixture();

        Integer roleCount = jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.tenant_roles
                        where tenant_id = :tenantId and template_code is not null
                        """)
                .param("tenantId", fixture.tenantId())
                .query(Integer.class)
                .single();
        assertEquals(FixedTenantRole.values().length, roleCount);

        for (Map.Entry<FixedTenantRole, UUID> role : fixture.roles().entrySet()) {
            Integer permissionCount = jdbcClient.sql("""
                            select count(*)
                            from zentro_desk.tenant_role_permissions
                            where role_id = :roleId
                            """)
                    .param("roleId", role.getValue())
                    .query(Integer.class)
                    .single();
            assertEquals(role.getKey().permissions().size(), permissionCount);
        }
    }

    private Fixture createFixture() {
        UUID tenantId = UUID.randomUUID();
        String tenantCode = "RBAC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, 'RBAC Tenant', :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("code", tenantCode)
                .update();

        Map<FixedTenantRole, UUID> roles = new EnumMap<>(FixedTenantRole.class);
        for (FixedTenantRole template : FixedTenantRole.values()) {
            UUID roleId = UUID.randomUUID();
            roles.put(template, roleId);
            jdbcClient.sql("""
                            insert into zentro_desk.tenant_roles
                                (id, created_at, updated_at, tenant_id, name, description, template_code, active)
                            values (:id, current_timestamp, current_timestamp, :tenantId, :name, :description,
                                    :templateCode, true)
                            """)
                    .param("id", roleId)
                    .param("tenantId", tenantId)
                    .param("name", template.displayName())
                    .param("description", template.description())
                    .param("templateCode", template.name())
                    .update();
            template.permissions().forEach(permission -> jdbcClient.sql("""
                            insert into zentro_desk.tenant_role_permissions (role_id, permission)
                            values (:roleId, :permission)
                            """)
                    .param("roleId", roleId)
                    .param("permission", permission)
                    .update());
            insertUser(tenantId, roleId, template);
        }
        return new Fixture(tenantId, tenantCode, roles);
    }

    private void insertUser(UUID tenantId, UUID roleId, FixedTenantRole role) {
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name, email, password_hash, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId, :firstName,
                                :email, :passwordHash, true)
                        """)
                .param("id", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("roleId", roleId)
                .param("firstName", role.displayName())
                .param("email", email(role))
                .param("passwordHash", passwordEncoder.encode(PASSWORD))
                .update();
    }

    private String login(String tenantCode, FixedTenantRole role) throws Exception {
        String body = mockMvc.perform(post("/api/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","tenantCode":"%s"}
                                """.formatted(email(role), PASSWORD, tenantCode)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher matcher = ACCESS_TOKEN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("Login response did not contain an access token");
        }
        return matcher.group(1);
    }

    private String email(FixedTenantRole role) {
        return role.name().toLowerCase() + "@rbac.test";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(UUID tenantId, String tenantCode, Map<FixedTenantRole, UUID> roles) {
    }
}
