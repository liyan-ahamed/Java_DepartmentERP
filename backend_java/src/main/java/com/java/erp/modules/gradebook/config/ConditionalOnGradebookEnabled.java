package com.java.erp.modules.gradebook.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The bean exists only when {@code features.gradebook.enabled=true}
 * (env {@code FEATURES_GRADEBOOK_ENABLED=true}). Default: disabled.
 *
 * Put on the marking controllers (/assessments, /grades, /internal-marks) and the gradebook
 * demo seeder. Services, repositories, entities and tables stay in place, so the feature can be
 * switched back on by configuration alone.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "features.gradebook.enabled", havingValue = "true")
public @interface ConditionalOnGradebookEnabled {
}
