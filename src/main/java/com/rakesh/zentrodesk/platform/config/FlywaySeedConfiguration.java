package com.rakesh.zentrodesk.platform.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(FlywaySeedProperties.class)
public class FlywaySeedConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.flyway.seed", name = "enabled", havingValue = "true")
    ApplicationRunner flywaySeedRunner(
            DataSource dataSource,
            FlywaySeedProperties seedProperties,
            @Value("${spring.flyway.default-schema:}") String defaultSchema,
            @Value("${spring.flyway.create-schemas:true}") boolean createSchemas
    ) {
        return args -> {
            FluentConfiguration configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(seedProperties.getLocations())
                    .table(seedProperties.getTable())
                    .createSchemas(createSchemas)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .sqlMigrationPrefix(seedProperties.getSqlMigrationPrefix())
                    .sqlMigrationSeparator(seedProperties.getSqlMigrationSeparator());

            if (StringUtils.hasText(defaultSchema)) {
                configuration.defaultSchema(defaultSchema);
            }

            configuration.load().migrate();
        };
    }
}
