package com.java.erp.modules.gradebook;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Finalization rules, autosave clearing, spreadsheet import, overview statistics and the CAT results view. */
class AssessmentWorkflowIntegrationTest extends GradebookTestBase {

    private SubjectOffering offering;
    private Student first;
    private Student second;

    @BeforeEach
    void setUpClass() {
        offering = theoryOffering();
        first = student(section);
        second = student(section);
    }

    private ResultActions finalizeAs(User user, Long id) throws Exception {
        return mvc.perform(post("/assessments/" + id + "/finalize").header("Authorization", token(user)));
    }

    private ResultActions postJson(User user, String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions getAs(User user, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", token(user)));
    }

    private static Map<String, Object> importRow(String registerNumber, Object marks, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("registerNumber", registerNumber);
        row.put("marks", marks);
        row.put("status", status);
        return row;
    }

    @Test
    void finalizationNeedsAMarkOrAbsentForEveryStudentAndThenLocksGrades() throws Exception {
        Assessment cat = draftAssessment(offering);

        finalizeAs(staff, cat.getId()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors", hasSize(2)));
        putGrades(staff, cat.getId(), List.of(row(first.getId(), 40, "GRADED"))).andExpect(status().isOk());
        finalizeAs(staff, cat.getId()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors", hasSize(1)));
        putGrades(staff, cat.getId(), List.of(row(second.getId(), null, "ABSENT"))).andExpect(status().isOk());

        finalizeAs(hod, cat.getId()).andExpect(status().isForbidden());
        finalizeAs(staff, cat.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FINALIZED"));
        finalizeAs(staff, cat.getId()).andExpect(status().isConflict());
        putGrades(staff, cat.getId(), List.of(row(first.getId(), 45, "GRADED"))).andExpect(status().isConflict());
    }

    @Test
    void autosaveValidatesAndCanClearAGrade() throws Exception {
        Assessment cat = draftAssessment(offering); // max 50
        putGrades(staff, cat.getId(), List.of(row(first.getId(), 50.5, "GRADED"))).andExpect(status().isBadRequest());
        putGrades(staff, cat.getId(), List.of(row(first.getId(), 12.345, "GRADED"))).andExpect(status().isBadRequest());
        putGrades(staff, cat.getId(), List.of(row(first.getId(), 12, null))).andExpect(status().isBadRequest());
        putGrades(otherStaff, cat.getId(), List.of(row(first.getId(), 12, "GRADED"))).andExpect(status().isForbidden());

        putGrades(staff, cat.getId(), List.of(row(first.getId(), 12.5, "GRADED"))).andExpect(status().isOk());
        putGrades(staff, cat.getId(), List.of(row(first.getId(), null, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.clearedCount").value(1));
        assertThat(gradeRepository.findByAssessmentId(cat.getId())).isEmpty();
    }

    @Test
    void importPreviewsThenAppliesOnlyWithExplicitOverwriteConfirmation() throws Exception {
        Assessment cat = draftAssessment(offering);
        grade(cat, first, 20, StudentGrade.GradeStatus.GRADED);
        String base = "/assessments/" + cat.getId() + "/grades/import";

        List<Map<String, Object>> withProblems = List.of(
                importRow(first.getRegisterNumber().toLowerCase(Locale.ROOT), 25, null),
                importRow(second.getRegisterNumber(), null, "ABSENT"),
                importRow("NOT-IN-CLASS", 10, null),
                importRow(second.getRegisterNumber() + " ", null, "ABSENT"));
        postJson(staff, base + "/preview", Map.of("rows", withProblems)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canApply").value(false))
                .andExpect(jsonPath("$.data.overwriteCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(3))
                .andExpect(jsonPath("$.data.rows[2].outcome").value("UNMATCHED"))
                .andExpect(jsonPath("$.data.rows[3].outcome").value("DUPLICATE"));
        postJson(staff, base, Map.of("rows", withProblems, "confirmOverwrite", true)).andExpect(status().isBadRequest());

        List<Map<String, Object>> clean = List.of(
                importRow(first.getRegisterNumber().toLowerCase(Locale.ROOT), 25, null),
                importRow(second.getRegisterNumber(), null, "ABSENT"));
        postJson(staff, base, Map.of("rows", clean)).andExpect(status().isConflict());
        assertThat(gradeRepository.findByAssessmentId(cat.getId())).hasSize(1)
                .allSatisfy(g -> assertThat(g.getMarks()).isEqualByComparingTo("20"));

        postJson(staff, base, Map.of("rows", clean, "confirmOverwrite", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(1));
        Map<Long, StudentGrade> stored = new HashMap<>();
        gradeRepository.findByAssessmentId(cat.getId()).forEach(g -> stored.put(g.getStudent().getId(), g));
        assertThat(stored.get(first.getId()).getMarks()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(stored.get(second.getId()).getGradeStatus()).isEqualTo(StudentGrade.GradeStatus.ABSENT);
    }

    @Test
    void overviewGroupsByClassAndNeverCountsAbsentAsZero() throws Exception {
        Student third = student(section);
        Student fourth = student(section);
        Assessment cat = draftAssessment(offering);
        grade(cat, first, 40, StudentGrade.GradeStatus.GRADED);
        grade(cat, second, 30, StudentGrade.GradeStatus.GRADED);
        grade(cat, third, null, StudentGrade.GradeStatus.ABSENT);
        String path = "/assessments/overview?batchId=" + batch.getId() + "&semester=3";

        getAs(staff, path).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].classSize").value(4))
                .andExpect(jsonPath("$.data[0].assessments[0].readyToFinalize").value(false))
                .andExpect(jsonPath("$.data[0].assessments[0].statistics.gradedCount").value(2))
                .andExpect(jsonPath("$.data[0].assessments[0].statistics.absentCount").value(1))
                .andExpect(jsonPath("$.data[0].assessments[0].statistics.notGradedCount").value(1))
                .andExpect(jsonPath("$.data[0].assessments[0].statistics.average").value(35.0))
                .andExpect(jsonPath("$.data[0].assessments[0].statistics.lowest").value(30.0))
                .andExpect(jsonPath("$.data[0].assessments[0].statistics.passPercentage").value(100.0));
        getAs(otherStaff, path).andExpect(jsonPath("$.data", hasSize(0)));
        getAs(hod, path).andExpect(jsonPath("$.data", hasSize(1)));
        assertThat(fourth.getId()).isNotNull();
    }

    @Test
    void catResultsViewShowsFinalizedAssessmentsOnly() throws Exception {
        Assessment finalized = finalizedAssessment(offering, "CAT 1", Assessment.AssessmentType.CAT_1);
        grade(finalized, first, 33, StudentGrade.GradeStatus.GRADED);
        Assessment draft = draftAssessment(offering);
        grade(draft, first, 44, StudentGrade.GradeStatus.GRADED);
        String path = "/results/by-exam?batchId=" + batch.getId() + "&semester=3&exam=CAT_1";

        getAs(staff, path).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[*].assessmentId", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(finalized.getId().intValue()))));
        getAs(staff, path.replace("CAT_1", "CAT_2")).andExpect(jsonPath("$.data.total_elements").value(0));
        getAs(otherStaff, path).andExpect(jsonPath("$.data.total_elements").value(0));
    }
}
