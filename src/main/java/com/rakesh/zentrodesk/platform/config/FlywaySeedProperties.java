package com.rakesh.zentrodesk.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.flyway.seed")
public class FlywaySeedProperties {

    private boolean enabled = false;

    private String[] locations = {"classpath:db/seed/dev"};

    private String table = "flyway_seed_schema_history";

    private String sqlMigrationPrefix = "";

    private String sqlMigrationSeparator = "-";

}
