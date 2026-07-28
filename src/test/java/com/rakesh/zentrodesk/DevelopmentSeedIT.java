package com.rakesh.zentrodesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "app.flyway.seed.enabled=true",
        "app.flyway.seed.locations=classpath:db/seed/dev"
})
class DevelopmentSeedIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void appliesFreshSeedWithFixedRolesAndDerivedNotificationTenants() {
        Integer templateCount = jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.tenant_roles
                        where tenant_id = '10000000-0000-0000-0000-000000000001'
                          and template_code is not null
                        """)
                .query(Integer.class)
                .single();
        Integer permissionCount = jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.tenant_role_permissions permission
                        join zentro_desk.tenant_roles role on role.id = permission.role_id
                        where role.tenant_id = '10000000-0000-0000-0000-000000000001'
                          and role.template_code is not null
                        """)
                .query(Integer.class)
                .single();
        Integer invalidNotifications = jdbcClient.sql("""
                        select count(*)
                        from zentro_desk.notifications notification
                        join zentro_desk.tenant_users tenant_user on tenant_user.id = notification.user_id
                        where notification.tenant_id is null
                           or notification.tenant_id <> tenant_user.tenant_id
                        """)
                .query(Integer.class)
                .single();

        assertEquals(5, templateCount);
        assertEquals(150, permissionCount);
        assertEquals(0, invalidNotifications);
    }
}
