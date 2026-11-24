package com.java.erp.modules.gradebook;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Gradebook (Score) is switched ON for local development, while the feature flag itself stays:
 * the base default is off and FEATURES_GRADEBOOK_ENABLED overrides both.
 */
class GradebookDevProfileConfigTest {

    @Test
    void devProfileEnablesGradebookAndFlagRemainsConfigurable() throws Exception {
        Properties dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));
        Properties base = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertThat(dev.getProperty("features.gradebook.enabled")).isEqualTo("${FEATURES_GRADEBOOK_ENABLED:true}");
        assertThat(base.getProperty("features.gradebook.enabled")).isEqualTo("${FEATURES_GRADEBOOK_ENABLED:false}");
    }
}
