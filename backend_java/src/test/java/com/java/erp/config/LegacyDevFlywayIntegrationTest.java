package com.java.erp.config;

import com.java.erp.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Reproduces a populated local database that never applied the destructive V10 seed. */
class LegacyDevFlywayIntegrationTest extends IntegrationTestBase {
    @Autowired private DataSource dataSource;

    @Test
    void legacyDatabaseKeepsItsRosterAndStillAppliesPendingSchemaMigrations() throws Exception {
        String schema = "flyway_legacy_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            try {
                Flyway.configure().dataSource(dataSource).schemas(schema)
                        .locations("classpath:db/migration").target("20").cleanDisabled(true).load().migrate();
                sql.execute("INSERT INTO " + schema + ".batches (batch_name, admission_year, graduation_year, current_year) "
                        + "VALUES ('LOCAL-KEEP', 2025, 2029, 2)");

                var strict = Flyway.configure().dataSource(dataSource).schemas(schema)
                        .locations("classpath:db/migration", "classpath:db/dev")
                        .ignoreMigrationPatterns("*:missing").cleanDisabled(true).load();
                var validation = strict.validateWithResult();
                assertThat(validation.validationSuccessful).isFalse();
                assertThat(validation.invalidMigrations).anySatisfy(m -> {
                    assertThat(m.version).isEqualTo("10");
                    assertThat(m.errorDetails.errorCode.name()).isEqualTo("RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED");
                });

                var dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));
                var compatible = Flyway.configure().dataSource(dataSource).schemas(schema)
                        .locations(dev.getProperty("spring.flyway.locations").split(","))
                        .ignoreMigrationPatterns(dev.getProperty("spring.flyway.ignore-migration-patterns").split(","))
                        .cleanDisabled(Boolean.parseBoolean(dev.getProperty("spring.flyway.clean-disabled"))).load();
                int pending = compatible.info().pending().length;
                assertThat(pending).isPositive();
                assertThat(compatible.migrate().migrationsExecuted).isEqualTo(pending);
                var accepted = compatible.validateWithResult();
                assertThat(accepted.validationSuccessful).withFailMessage("%s", accepted.invalidMigrations.stream()
                        .map(m -> m.version + ": " + m.errorDetails.errorMessage).toList()).isTrue();
                assertThat(compatible.info().pending()).isEmpty();
                assertThat(compatible.migrate().migrationsExecuted).isZero();
                try (var rows = sql.executeQuery("SELECT batch_name FROM " + schema + ".batches")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("LOCAL-KEEP");
                    assertThat(rows.next()).isFalse();
                }
                try (var rows = sql.executeQuery("SELECT count(*) FROM " + schema
                        + ".flyway_schema_history WHERE version = '10'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }

                // The compatibility setting must not hide changed historical migrations.
                sql.executeUpdate("UPDATE " + schema + ".flyway_schema_history SET checksum = 0 WHERE version = '20'");
                var changed = compatible.validateWithResult();
                assertThat(changed.validationSuccessful).isFalse();
                assertThat(changed.invalidMigrations).anySatisfy(m -> {
                    assertThat(m.version).isEqualTo("20");
                    assertThat(m.errorDetails.errorCode.name()).isEqualTo("CHECKSUM_MISMATCH");
                });
            } finally {
                // Only this test's uniquely named schema in erp_db_test is removed.
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
