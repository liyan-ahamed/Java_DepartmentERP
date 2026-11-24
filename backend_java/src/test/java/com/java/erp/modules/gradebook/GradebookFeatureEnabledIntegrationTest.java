package com.java.erp.modules.gradebook;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.controller.AssessmentController;
import com.java.erp.modules.gradebook.controller.InternalMarksController;
import com.java.erp.modules.gradebook.controller.StudentGradeController;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** features.gradebook.enabled=true (set by GradebookTestBase) brings every marking API back. */
class GradebookFeatureEnabledIntegrationTest extends GradebookTestBase {

    @Autowired private ApplicationContext context;

    @Test
    void enablingTheFlagRestoresTheMarkingApis() throws Exception {
        assertThat(context.getBeansOfType(AssessmentController.class)).hasSize(1);
        assertThat(context.getBeansOfType(StudentGradeController.class)).hasSize(1);
        assertThat(context.getBeansOfType(InternalMarksController.class)).hasSize(1);

        StudentLogin student = studentLogin(section);
        SubjectOffering offering = theoryOffering();

        mvc.perform(get("/assessments").header("Authorization", token(hod))).andExpect(status().isOk());
        mvc.perform(get("/grades/me").header("Authorization", token(student.user()))).andExpect(status().isOk());
        mvc.perform(get("/internal-marks/me").header("Authorization", token(student.user()))).andExpect(status().isOk());
        mvc.perform(get("/subject-offerings/" + offering.getId() + "/internal-marks").header("Authorization", token(staff)))
                .andExpect(status().isOk());
    }
}
