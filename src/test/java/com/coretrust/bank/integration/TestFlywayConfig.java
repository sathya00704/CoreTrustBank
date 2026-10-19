package com.coretrust.bank.integration;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Test profile only: wipe coretrust_test and rebuild it from the migrations on every run. */
@Configuration
@Profile("test")
public class TestFlywayConfig {

    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}