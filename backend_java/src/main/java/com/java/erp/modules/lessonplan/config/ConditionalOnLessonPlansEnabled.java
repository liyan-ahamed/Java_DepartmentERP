package com.java.erp.modules.lessonplan.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The bean exists only when {@code features.lesson-plans.enabled=true}
 * (env {@code FEATURES_LESSON_PLANS_ENABLED=true}). Default: disabled — Lesson Plans are not an
 * active product feature.
 *
 * Put on the /lesson-plans controller. Service, repository, entity, DTOs and the lesson_plans table
 * stay in place (dev seeding of lesson plans checks the same flag), so the feature can be switched
 * back on by configuration alone.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = LessonPlansFeature.PROPERTY, havingValue = "true")
public @interface ConditionalOnLessonPlansEnabled {
}
