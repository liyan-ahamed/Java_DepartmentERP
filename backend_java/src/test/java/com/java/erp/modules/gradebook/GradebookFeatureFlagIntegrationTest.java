package com.java.erp.modules.gradebook;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.controller.AssessmentController;
import com.java.erp.modules.gradebook.controller.InternalMarksController;
import com.java.erp.modules.gradebook.controller.StudentGradeController;
import com.java.erp.support.AcademicTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default configuration (features.gradebook.enabled not set): the marking APIs do not exist,
 * while subjects, offerings, batches and sections keep working. The gradebook's own tests run
 * with the flag switched on (see GradebookTestBase / GradebookFeatureEnabledIntegrationTest).
 */
class GradebookFeatureFlagIntegrationTest extends AcademicTestBase {

    @Autowired private ApplicationContext context;

    @Test
    void markingControllersAreNotRegisteredByDefault() {
        assertThat(context.getBeansOfType(AssessmentController.class)).isEmpty();
        assertThat(context.getBeansOfType(StudentGradeController.class)).isEmpty();
        assertThat(context.getBeansOfType(InternalMarksController.class)).isEmpty();
    }

    @Test
    void markingEndpointsAreNotExposed() throws Exception {
        StudentLogin student = studentLogin(section);
        SubjectOffering offering = theoryOffering();

        mvc.perform(get("/assessments").header("Authorization", token(hod))).andExpect(status().isNotFound());
        mvc.perform(get("/assessments").header("Authorization", token(staff))).andExpect(status().isNotFound());
        mvc.perform(get("/assessments/1").header("Authorization", token(staff))).andExpect(status().isNotFound());
        mvc.perform(post("/assessments").header("Authorization", token(staff))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(put("/assessments/1/grades").header("Authorization", token(staff))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/assessments/1/finalize").header("Authorization", token(staff))).andExpect(status().isNotFound());
        mvc.perform(get("/grades/me").header("Authorization", token(student.user()))).andExpect(status().isNotFound());
        mvc.perform(get("/internal-marks/me").header("Authorization", token(student.user()))).andExpect(status().isNotFound());
        mvc.perform(get("/subject-offerings/" + offering.getId() + "/internal-marks").header("Authorization", token(hod)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsStillNeedALogin() throws Exception {
        mvc.perform(get("/assessments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/grades/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void academicLookupApisStayEnabled() throws Exception {
        SubjectOffering offering = theoryOffering();

        mvc.perform(get("/subjects").header("Authorization", token(hod))).andExpect(status().isOk());
        mvc.perform(get("/subject-offerings").header("Authorization", token(hod))).andExpect(status().isOk());
        mvc.perform(get("/subject-offerings/" + offering.getId()).header("Authorization", token(staff))).andExpect(status().isOk());
        mvc.perform(get("/batches").header("Authorization", token(staff))).andExpect(status().isOk());
        mvc.perform(get("/sections").header("Authorization", token(hod))).andExpect(status().isOk());
    }
}
