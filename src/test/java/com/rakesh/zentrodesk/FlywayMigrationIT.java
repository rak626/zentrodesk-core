package com.rakesh.zentrodesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationIT extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void appliesMigrationsToApplicationSchema() {
        Integer tableCount = jdbcClient.sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'zentro_desk'
                        """)
                .query(Integer.class)
                .single();

        assertTrue(tableCount > 0);
    }
}
