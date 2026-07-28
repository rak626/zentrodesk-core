package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.common.web.CorrelationIdFilter;
import com.rakesh.zentrodesk.iam.principal.TenantPrincipal;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import com.rakesh.zentrodesk.platform.audit.AuditEventResponse;
import com.rakesh.zentrodesk.platform.audit.AuditEventService;
import com.rakesh.zentrodesk.sales.lead.api.LeadRequest;
import com.rakesh.zentrodesk.sales.lead.api.LeadResponse;
import com.rakesh.zentrodesk.sales.lead.api.LeadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private LeadService leadService;

    @Autowired
    private AuditEventService auditEventService;

    @AfterEach
    void clearContexts() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        tenantContext.clear();
    }

    @Test
    void recordsSanitizedCorrelatedEventsAndScopesReadsToTenant() {
        UUID tenantA = insertTenant("audit-a");
        UUID tenantB = insertTenant("audit-b");
        UUID actorId = UUID.randomUUID();
        authenticate(tenantA, actorId);
        requestCorrelation("audit-request-123");

        LeadResponse lead = leadService.create(sensitiveLead());

        Map<String, Object> stored = jdbcClient.sql("""
                        select actor_id, entity_type, entity_id, action, correlation_id,
                               before_summary, after_summary
                        from zentro_desk.audit_events
                        where tenant_id = :tenantId
                        """)
                .param("tenantId", tenantA)
                .query()
                .singleRow();
        assertEquals(actorId, stored.get("actor_id"));
        assertEquals("LEAD", stored.get("entity_type"));
        assertEquals(lead.id(), stored.get("entity_id"));
        assertEquals("CREATE", stored.get("action"));
        assertEquals("audit-request-123", stored.get("correlation_id"));
        assertNull(stored.get("before_summary"));
        String afterSummary = (String) stored.get("after_summary");
        assertFalse(afterSummary.contains("Sensitive Traveller"));
        assertFalse(afterSummary.contains("sensitive@example.com"));
        assertFalse(afterSummary.contains("9999999999"));

        PageResponse<AuditEventResponse> ownEvents = auditEventService.findAll(
                0, 20, "occurredAt", "DESC", "lead", lead.id(), "create", Instant.EPOCH, Instant.now().plusSeconds(5)
        );
        assertEquals(1, ownEvents.totalElements());

        authenticate(tenantB, UUID.randomUUID());
        PageResponse<AuditEventResponse> foreignEvents = auditEventService.findAll(
                0, 20, "occurredAt", "DESC", null, null, null, null, null
        );
        assertEquals(0, foreignEvents.totalElements());
    }

    @Test
    void databaseRejectsAuditUpdatesAndDeletes() {
        UUID tenantId = insertTenant("immutable");
        authenticate(tenantId, UUID.randomUUID());
        LeadResponse lead = leadService.create(sensitiveLead());
        UUID auditId = jdbcClient.sql("""
                        select id from zentro_desk.audit_events
                        where tenant_id = :tenantId and entity_id = :entityId
                        """)
                .param("tenantId", tenantId)
                .param("entityId", lead.id())
                .query(UUID.class)
                .single();

        assertThrows(DataAccessException.class, () -> jdbcClient.sql("""
                        update zentro_desk.audit_events set action = 'CHANGED' where id = :id
                        """)
                .param("id", auditId)
                .update());
        assertThrows(DataAccessException.class, () -> jdbcClient.sql("""
                        delete from zentro_desk.audit_events where id = :id
                        """)
                .param("id", auditId)
                .update());
    }

    @Test
    void auditPersistenceFailureRollsBackTheBusinessMutation() {
        UUID tenantId = insertTenant("rollback");
        authenticate(tenantId, UUID.randomUUID());
        requestCorrelation("x".repeat(65));

        assertThrows(DataAccessException.class, () -> leadService.create(sensitiveLead()));

        Integer leadCount = jdbcClient.sql("""
                        select count(*) from zentro_desk.leads
                        where tenant_id = :tenantId and name = 'Sensitive Traveller'
                        """)
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single();
        Integer auditCount = jdbcClient.sql("""
                        select count(*) from zentro_desk.audit_events where tenant_id = :tenantId
                        """)
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single();
        assertEquals(0, leadCount);
        assertEquals(0, auditCount);
    }

    private LeadRequest sensitiveLead() {
        return new LeadRequest(null, "Sensitive Traveller", "9999999999", "sensitive@example.com", null,
                null, null, null, null, null, null, "private remarks", null);
    }

    private UUID insertTenant(String prefix) {
        UUID tenantId = UUID.randomUUID();
        String code = prefix + "-" + tenantId.toString().substring(0, 8);
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, :name, :code,
                                'Asia/Kolkata', 'INR', true)
                        """)
                .param("id", tenantId)
                .param("name", code)
                .param("code", code)
                .update();
        return tenantId;
    }

    private void authenticate(UUID tenantId, UUID actorId) {
        UUID roleId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_roles
                            (id, created_at, updated_at, tenant_id, name, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :name, true)
                        """)
                .param("id", roleId)
                .param("tenantId", tenantId)
                .param("name", "Audit " + actorId.toString().substring(0, 8))
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.tenant_users
                            (id, created_at, updated_at, tenant_id, role_id, first_name,
                             email, password_hash, active, session_version)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :roleId,
                                'Audit', :email, 'test-password-hash', true, 0)
                        """)
                .param("id", actorId)
                .param("tenantId", tenantId)
                .param("roleId", roleId)
                .param("email", actorId + "@example.test")
                .update();
        tenantContext.setTenantId(tenantId);
        TenantPrincipal principal = new TenantPrincipal(actorId, tenantId, "auditor@example.com", "", "Audit", "User",
                roleId, "Owner", true, 0, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private void requestCorrelation(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, correlationId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
