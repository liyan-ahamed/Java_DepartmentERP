package com.java.erp.config;

import com.java.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that demo data and demo logins only reach dev/test databases.
 */
class ProfileSafetyIntegrationTest extends IntegrationTestBase {

    @Autowired private ApplicationContext context;

    @Test
    void demoLoginSeederIsNotActiveOutsideDevProfile() {
        assertThat(context.getBeansOfType(DataSeeder.class)).isEmpty();
    }

    @Test
    void gradebookDemoSeederIsNotActiveOutsideDevProfile() {
        assertThat(context.getBeansOfType(GradebookDevSeeder.class)).isEmpty();
    }

    @Test
    void defaultFlywayLocationExcludesDemoData() throws Exception {
        Properties defaults = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        Properties dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));

        assertThat(defaults.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(dev.getProperty("spring.flyway.locations")).contains("classpath:db/dev");
        assertThat(new ClassPathResource("db/migration/V10__seed_department_data.sql").exists()).isFalse();
        assertThat(new ClassPathResource("db/dev/V10__seed_department_data.sql").exists()).isTrue();
    }

    @Test
    void jwtSecretHasNoDefaultValue() throws Exception {
        Properties defaults = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(defaults.getProperty("app.security.jwt.secret")).isEqualTo("${JWT_SECRET:}");
    }
}
