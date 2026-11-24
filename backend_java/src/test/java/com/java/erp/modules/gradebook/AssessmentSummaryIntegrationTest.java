package com.java.erp.modules.gradebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade.GradeStatus;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssessmentSummaryIntegrationTest extends GradebookTestBase {

    private JsonNode summary(Assessment assessment) throws Exception {
        return data(mvc.perform(get("/assessments/" + assessment.getId() + "/summary").header("Authorization", token(staff)))
                .andExpect(status().isOk()));
    }

    @Test
    void statisticsUseGradedMarksOnly() throws Exception {
        SubjectOffering offering = theoryOffering();
        Assessment cat = assessment(offering, "CAT 1", Assessment.AssessmentType.CAT_1, 50, 25);
        grade(cat, student(section), 40, GradeStatus.GRADED);
        grade(cat, student(section), 20, GradeStatus.GRADED);
        grade(cat, student(section), 25, GradeStatus.GRADED);
        grade(cat, student(section), 30, GradeStatus.GRADED);
        grade(cat, student(section), null, GradeStatus.ABSENT);
        grade(cat, student(section), null, GradeStatus.EXEMPTED);
        student(section); // not graded yet
        student(otherSection); // not in this class

        JsonNode s = summary(cat);

        assertThat(s.get("expectedStudentCount").asInt()).isEqualTo(7);
        assertThat(s.get("gradedCount").asInt()).isEqualTo(4);
        assertThat(s.get("absentCount").asInt()).isEqualTo(1);
        assertThat(s.get("exemptedCount").asInt()).isEqualTo(1);
        assertThat(s.get("notGradedCount").asInt()).isEqualTo(1);
        assertThat(s.get("average").decimalValue()).isEqualByComparingTo("28.75");
        assertThat(s.get("highest").decimalValue()).isEqualByComparingTo("40");
        assertThat(s.get("lowest").decimalValue()).isEqualByComparingTo("20");
        // pass marks 25 (inclusive): 40, 25, 30 pass; 20 fails
        assertThat(s.get("passCount").asInt()).isEqualTo(3);
        assertThat(s.get("failCount").asInt()).isEqualTo(1);
        assertThat(s.get("passPercentage").decimalValue()).isEqualByComparingTo("75.00");
    }

    @Test
    void passFieldsAreNullWithoutPassMarksAndStatsNullWithoutGrades() throws Exception {
        SubjectOffering offering = theoryOffering();
        Assessment assignment = assessment(offering, "Assignment 1", Assessment.AssessmentType.ASSIGNMENT, 10, null);
        grade(assignment, student(section), 7, GradeStatus.GRADED);

        JsonNode s = summary(assignment);
        assertThat(s.get("average").decimalValue()).isEqualByComparingTo("7");
        assertThat(s.get("passCount").isNull()).isTrue();
        assertThat(s.get("passPercentage").isNull()).isTrue();

        Assessment empty = assessment(offering, "Quiz", Assessment.AssessmentType.QUIZ, 10, 5);
        JsonNode e = summary(empty);
        assertThat(e.get("gradedCount").asInt()).isZero();
        assertThat(e.get("average").isNull()).isTrue();
        assertThat(e.get("highest").isNull()).isTrue();
        assertThat(e.get("passCount").asInt()).isZero();
        assertThat(e.get("passPercentage").isNull()).isTrue();
    }

    @Test
    void summaryAccessFollowsOfferingOwnership() throws Exception {
        Assessment cat = draftAssessment(theoryOffering());
        mvc.perform(get("/assessments/" + cat.getId() + "/summary").header("Authorization", token(hod)))
                .andExpect(status().isOk());
        mvc.perform(get("/assessments/" + cat.getId() + "/summary").header("Authorization", token(otherStaff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assessments/999999999/summary").header("Authorization", token(hod)))
                .andExpect(status().isNotFound());
    }
}
