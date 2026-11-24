package com.java.erp.modules.lessonplan;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.lessonplan.config.LessonPlansFeature;
import com.java.erp.modules.lessonplan.controller.LessonPlanController;
import com.java.erp.modules.lessonplan.service.LessonPlanService;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default configuration: Lesson Plans are switched off, the endpoints do not exist, but the
 * implementation (service, repository, table) is still there. LessonPlanIntegrationTest runs the
 * full feature with features.lesson-plans.enabled=true.
 */
class LessonPlanFeatureFlagIntegrationTest extends ErpModulesTestBase {

    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;

    @Test
    void disabledByDefault() {
        assertThat(environment.getProperty(LessonPlansFeature.PROPERTY, Boolean.class, false)).isFalse();
        assertThat(context.getBeansOfType(LessonPlanController.class)).isEmpty();
        assertThat(context.getBeansOfType(LessonPlanService.class)).hasSize(1);
    }

    @Test
    void endpointsAreUnavailableWhenDisabled() throws Exception {
        SubjectOffering offering = theoryOffering();
        StudentLogin student = studentLogin(section);
        postAs(staff, "/lesson-plans", Map.of("subjectOfferingId", offering.getId(), "unitNumber", 1, "topic", "Arrays",
                "plannedDate", "2026-10-20")).andExpect(status().isNotFound());
        getAs(hod, "/lesson-plans").andExpect(status().isNotFound());
        getAs(student.user(), "/lesson-plans/me").andExpect(status().isNotFound());
        getAs(staff, "/lesson-plans/subject-offerings/" + offering.getId() + "/progress").andExpect(status().isNotFound());
        mvc.perform(get("/lesson-plans")).andExpect(status().isUnauthorized());
    }
}
