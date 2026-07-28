package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.platform.role.FixedTenantRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@AutoConfigureMockMvc
class GlobalSearchIT extends PostgreSqlIntegrationTest {

    private static final String PASSWORD = "SecurePass1!";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchIsTenantScopedAndOmitsGroupsTheFixedRoleCannotRead() throws Exception {
        Fixture tenantA = createFixture("A");
        Fixture tenantB = createFixture("B");
        insertSearchRecords(tenantA, "Visible Needle");
        insertSearchRecords(tenantB, "Foreign Needle");

        JsonNode sales = search(login(tenantA, FixedTenantRole.SALES), "Needle");
        assertEquals(
                Set.of("CUSTOMERS", "LEADS", "QUOTATIONS", "BOOKINGS"),
                groupNames(sales)
        );
        assertTrue(sales.toString().contains("Visible Needle"));
        assertFalse(sales.toString().contains("Foreign Needle"));

        JsonNode accounts = search(login(tenantA, FixedTenantRole.ACCOUNTS), "Needle");
        assertEquals(Set.of("CUSTOMERS", "BOOKINGS"), groupNames(accounts));
        assertFalse(accounts.toString().contains("\"LEADS\""));
        assertFalse(accounts.toString().contains("\"QUOTATIONS\""));
    }

    @Test
    void rejectsShortQueriesAndOutOfRangeGroupLimits() throws Exception {
        Fixture fixture = createFixture("limits");
        String token = login(fixture, FixedTenantRole.OWNER);

        mockMvc.perform(get("/api/app/search")
                        .param("q", "x")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/app/search")
                        .param("q", "valid")
                        .param("limit", "11")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/app/search")
                        .param("q", "valid")
                        .param("groups", "CUSTOMERS,BOOKINGS")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void customerLookupIsTenantScopedAndReturnsOnlyOperationalLabels() throws Exception {
        Fixture tenantA = createFixture("lookup-a");
        Fixture tenantB = createFixture("lookup-b");
        insertSearchRecords(tenantA, "Visible Lookup");
        insertSearchRecords(tenantB, "Foreign Lookup");
        String token = login(tenantA, FixedTenantRole.SALES);

        String content = mockMvc.perform(get("/api/app/customers/lookup")
                        .param("query", "Lookup")
                        .param("limit", "10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(content).path("data");
        assertEquals(1, data.size());
        assertEquals("Visible Lookup", data.get(0).path("name").asText());
        assertFalse(content.contains("Foreign Lookup"));
        assertFalse(data.get(0).has("phone"));
        assertFalse(data.get(0).has("email"));

        mockMvc.perform(get("/api/app/customers/lookup")
                        .param("limit", "51")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private Fixture createFixture(String suffix) {
        UUID tenantId = UUID.randomUUID();
        String tenantCode = "SEARCH-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, :name, :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("name", "Search tenant " + suffix)
                .param("code", tenantCode)
                .update();

        for (FixedTenantRole role : Set.of(FixedTenantRole.OWNER, FixedTenantRole.SALES, FixedTenantRole.ACCOUNTS)) {
            UUID roleId = UUID.randomUUID();
            jdbcClient.sql("""
                            insert into zentro_desk.tenant_roles
                                (id, created_at, updated_at, tenant_id, name, template_code, active)
                            values (:id, current_timestamp, current_timestamp, :tenantId, :name, :templateCode, true)
                            """)
                    .param("id", roleId)
                    .param("tenantId", tenantId)
                    .param("name", role.displayName())
                    .param("templateCode", role.name())
                    .update();
            role.permissions().forEach(permission -> jdbcClient.sql("""
                            insert into zentro_desk.tenant_role_permissions (role_id, permission)
                            values (:roleId, :permission)
                            """)
                    .param("roleId", roleId)
                    .param("permission", permission)
                    .update());
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
                    .param("email", email(suffix, role))
                    .param("passwordHash", passwordEncoder.encode(PASSWORD))
                    .update();
        }
        return new Fixture(tenantId, tenantCode, suffix);
    }

    private void insertSearchRecords(Fixture fixture, String label) {
        UUID customerId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.customers
                            (id, created_at, updated_at, tenant_id, name, city, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, 'Pune', true)
                        """)
                .param("id", customerId)
                .param("tenantId", fixture.tenantId())
                .param("name", label)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.leads
                            (id, created_at, updated_at, tenant_id, name, destination, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, 'Goa', 'NEW', true)
                        """)
                .param("id", UUID.randomUUID())
                .param("tenantId", fixture.tenantId())
                .param("name", label)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.quotations
                            (id, created_at, updated_at, tenant_id, customer_id, quotation_number,
                             total, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :customerId,
                                :number, 1000, 'DRAFT', true)
                        """)
                .param("id", UUID.randomUUID())
                .param("tenantId", fixture.tenantId())
                .param("customerId", customerId)
                .param("number", label.replace(" ", "-"))
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.bookings
                            (id, created_at, updated_at, tenant_id, customer_id, booking_number, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :customerId,
                                :number, 'CONFIRMED', true)
                        """)
                .param("id", UUID.randomUUID())
                .param("tenantId", fixture.tenantId())
                .param("customerId", customerId)
                .param("number", label.replace(" ", "-") + "-" + fixture.suffix())
                .update();
    }

    private JsonNode search(String token, String query) throws Exception {
        String content = mockMvc.perform(get("/api/app/search")
                        .param("q", query)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content).path("data").path("groups");
    }

    private Set<String> groupNames(JsonNode groups) {
        return StreamSupport.stream(groups.spliterator(), false)
                .map(group -> group.path("group").asText())
                .collect(Collectors.toSet());
    }

    private String login(Fixture fixture, FixedTenantRole role) throws Exception {
        String body = mockMvc.perform(post("/api/app/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s","tenantCode":"%s"}
                                """.formatted(email(fixture.suffix(), role), PASSWORD, fixture.tenantCode())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher matcher = ACCESS_TOKEN.matcher(body);
        if (!matcher.find()) throw new IllegalStateException("Access token missing");
        return matcher.group(1);
    }

    private String email(String suffix, FixedTenantRole role) {
        return role.name().toLowerCase() + "-" + suffix.toLowerCase() + "@search.test";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(UUID tenantId, String tenantCode, String suffix) {
    }
}
