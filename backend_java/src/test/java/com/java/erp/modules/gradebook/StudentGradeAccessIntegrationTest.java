package com.java.erp.modules.gradebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentGradeAccessIntegrationTest extends GradebookTestBase {

    private JsonNode myGrades(StudentLogin login, String... params) throws Exception {
        var request = get("/grades/me").header("Authorization", token(login.user()));
        for (int i = 0; i < params.length; i += 2) request = request.param(params[i], params[i + 1]);
        return data(mvc.perform(request).andExpect(status().isOk()));
    }

    private List<Long> assessmentIds(JsonNode grades) {
        List<Long> ids = new ArrayList<>();
        grades.forEach(g -> ids.add(g.get("assessmentId").asLong()));
        return ids;
    }

    @Test
    void studentSeesOwnFinalizedGradesOnly() throws Exception {
        SubjectOffering offering = theoryOffering();
        StudentLogin me = studentLogin(section);
        StudentLogin classmate = studentLogin(section);
        Assessment published = finalizedAssessment(offering, "CAT 1", Assessment.AssessmentType.CAT_1);
        Assessment draft = assessment(offering, "CAT 2", Assessment.AssessmentType.CAT_2, 50, 25);
        grade(published, me.student(), 41, StudentGrade.GradeStatus.GRADED);
        grade(published, classmate.student(), 12, StudentGrade.GradeStatus.GRADED);
        grade(draft, me.student(), 33, StudentGrade.GradeStatus.GRADED);

        JsonNode grades = myGrades(me);

        assertThat(assessmentIds(grades)).containsExactly(published.getId());
        JsonNode result = grades.get(0);
        assertThat(result.get("marks").decimalValue()).isEqualByComparingTo("41");
        assertThat(result.get("gradeStatus").asText()).isEqualTo("GRADED");
        assertThat(result.get("subjectCode").asText()).isEqualTo(offering.getSubject().getCode());
        assertThat(result.get("subjectType").asText()).isEqualTo("THEORY");
        assertThat(result.get("assessmentType").asText()).isEqualTo("CAT_1");
        assertThat(result.get("maxMarks").decimalValue()).isEqualByComparingTo("50");
        // nothing about the classmate leaks into my response
        assertThat(grades.toString()).doesNotContain(classmate.student().getRegisterNumber());
    }

    @Test
    void draftGradesAreNeverExposedToStudents() throws Exception {
        SubjectOffering offering = theoryOffering();
        StudentLogin me = studentLogin(section);
        grade(draftAssessment(offering), me.student(), 33, StudentGrade.GradeStatus.GRADED);

        assertThat(myGrades(me)).isEmpty();
    }

    @Test
    void studentOnlySeesTheirOwnClassAndOwnMarks() throws Exception {
        StudentLogin me = studentLogin(section);
        StudentLogin elsewhere = studentLogin(otherSection);
        Assessment otherClass = finalizedAssessment(offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff),
                "CAT 1", Assessment.AssessmentType.CAT_1);
        grade(otherClass, elsewhere.student(), 47, StudentGrade.GradeStatus.GRADED);
        Assessment mine = finalizedAssessment(theoryOffering(), "Quiz", Assessment.AssessmentType.QUIZ);

        assertThat(assessmentIds(myGrades(me))).containsExactly(mine.getId());
        JsonNode myOnly = myGrades(me).get(0);
        assertThat(myOnly.get("gradeStatus").asText()).isEqualTo("NOT_GRADED");
        assertThat(assessmentIds(myGrades(elsewhere))).containsExactly(otherClass.getId());
    }

    @Test
    void filtersNarrowTheResult() throws Exception {
        StudentLogin me = studentLogin(section);
        SubjectOffering first = theoryOffering();
        SubjectOffering second = offering(subject(Subject.SubjectType.THEORY_CUM_PRACTICAL), section, staff);
        finalizedAssessment(first, "CAT 1", Assessment.AssessmentType.CAT_1);
        Assessment practical = finalizedAssessment(second, "Lab 1", Assessment.AssessmentType.PRACTICAL);

        assertThat(assessmentIds(myGrades(me, "subjectId", second.getSubject().getId().toString())))
                .containsExactly(practical.getId());
        assertThat(myGrades(me, "semester", "5")).isEmpty();
        assertThat(myGrades(me, "academicYear", ACADEMIC_YEAR)).hasSize(2);
    }

    @Test
    void studentsCannotReachStaffGradeEndpoints() throws Exception {
        StudentLogin me = studentLogin(section);
        Assessment published = finalizedAssessment(theoryOffering(), "CAT 1", Assessment.AssessmentType.CAT_1);

        mvc.perform(get("/assessments/" + published.getId() + "/grades").header("Authorization", token(me.user())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assessments/" + published.getId() + "/summary").header("Authorization", token(me.user())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assessments/" + published.getId()).header("Authorization", token(me.user())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/grades/me").header("Authorization", token(staff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/grades/me")).andExpect(status().isUnauthorized());
    }
}
