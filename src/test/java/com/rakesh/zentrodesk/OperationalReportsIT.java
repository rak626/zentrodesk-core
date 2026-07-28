package com.rakesh.zentrodesk;

import com.rakesh.zentrodesk.analytics.report.OperationalReportResponse;
import com.rakesh.zentrodesk.analytics.report.OperationalReportService;
import com.rakesh.zentrodesk.analytics.report.ReportType;
import com.rakesh.zentrodesk.iam.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationalReportsIT extends PostgreSqlIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("91000000-0000-0000-0000-000000000101");
    private static final UUID ACTOR_ID = UUID.fromString("91000000-0000-0000-0000-000000000201");
    private static final UUID LEAD_ID = UUID.fromString("91000000-0000-0000-0000-000000000301");

    @Autowired
    private OperationalReportService reportService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeAll
    void createFixture() {
        jdbcClient.sql("""
                        insert into zentro_desk.tenants
                            (id, created_at, updated_at, name, code, timezone, currency, active)
                        values (:id, current_timestamp, current_timestamp, 'Report Tenant', 'REPORT-IT',
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
                                'Report', 'report-it@example.com', 'unused', true)
                        """)
                .param("id", ACTOR_ID)
                .param("tenantId", TENANT_ID)
                .param("roleId", ROLE_ID)
                .update();
        jdbcClient.sql("""
                        insert into zentro_desk.leads
                            (id, created_at, updated_at, tenant_id, assigned_user_id, name, status, active)
                        values (:id, current_timestamp, current_timestamp, :tenantId, :userId,
                                'Report lead', 'WON', true)
                        """)
                .param("id", LEAD_ID)
                .param("tenantId", TENANT_ID)
                .param("userId", ACTOR_ID)
                .update();
    }

    @BeforeEach
    void selectTenant() {
        tenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        tenantContext.clear();
    }

    @Test
    void returnsAllAggregatesAndTenantQualifiedDrillDowns() {
        LocalDate from = LocalDate.now().minusDays(180);
        LocalDate to = LocalDate.now().plusDays(180);

        OperationalReportResponse response = reportService.summary(from, to, "Asia/Kolkata", null);

        assertEquals(5, response.sections().size());
        for (ReportType type : ReportType.values()) {
            assertTrue(reportService.details(type, from, to, "Asia/Kolkata", null, 0, 50)
                    .content()
                    .stream()
                    .allMatch(row -> !row.values().isEmpty()));
        }
    }

    @Test
    void csvExportEscapesFormulaCellsAndRecordsAudit() throws Exception {
        jdbcClient.sql("""
                        update zentro_desk.leads
                        set name = '=SUM(1,1)'
                        where id = :leadId
                        """)
                .param("leadId", LEAD_ID)
                .update();
        StringWriter writer = new StringWriter();

        reportService.exportCsv(
                ReportType.LEAD_CONVERSION,
                LocalDate.now().minusDays(180),
                LocalDate.now().plusDays(180),
                "Asia/Kolkata",
                null,
                TENANT_ID,
                ACTOR_ID,
                writer
        );

        assertTrue(writer.toString().contains("\"'=SUM(1,1)\""));
        assertEquals(1, jdbcClient.sql("""
                        select count(*) from zentro_desk.audit_events
                        where tenant_id = :tenantId
                          and actor_id = :actorId
                          and entity_type = 'REPORT'
                          and action = 'EXPORT'
                        """)
                .param("tenantId", TENANT_ID)
                .param("actorId", ACTOR_ID)
                .query(Integer.class)
                .single());
    }
}
