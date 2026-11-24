package com.java.erp.modules.gradebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradeEntryIntegrationTest extends GradebookTestBase {

    private Assessment draft;
    private Student first;
    private Student second;
    private Student third;

    @BeforeEach
    void setUp() {
        draft = draftAssessment(theoryOffering()); // max 50
        first = student(section);
        second = student(section);
        third = student(section);
    }

    private Map<Long, StudentGrade> stored() {
        return gradeRepository.findByAssessmentId(draft.getId()).stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), Function.identity()));
    }

    @Test
    void bulkInsertCreatesAllRows() throws Exception {
        JsonNode result = data(putGrades(staff, draft.getId(), List.of(
                row(first.getId(), 18, "GRADED"),
                row(second.getId(), null, "ABSENT"),
                row(third.getId(), null, "EXEMPTED"))).andExpect(status().isOk()));

        assertThat(result.get("createdCount").asInt()).isEqualTo(3);
        assertThat(result.get("changedGradeCount").asInt()).isEqualTo(3);
        Map<Long, StudentGrade> grades = stored();
        assertThat(grades.get(first.getId()).getMarks()).isEqualByComparingTo("18");
        assertThat(grades.get(second.getId()).getGradeStatus()).isEqualTo(StudentGrade.GradeStatus.ABSENT);
        assertThat(grades.get(third.getId()).getMarks()).isNull();
    }

    @Test
    void bulkUpdateChangesExistingRowsAndCountsOnlyRealChanges() throws Exception {
        putGrades(staff, draft.getId(), List.of(row(first.getId(), 18, "GRADED"), row(second.getId(), null, "ABSENT")))
                .andExpect(status().isOk());

        JsonNode result = data(putGrades(staff, draft.getId(), List.of(
                row(first.getId(), 18, "GRADED"),       // unchanged
                row(second.getId(), 42.5, "GRADED"),    // ABSENT -> GRADED
                row(third.getId(), 30, "GRADED")))      // new
                .andExpect(status().isOk()));

        assertThat(result.get("createdCount").asInt()).isEqualTo(1);
        assertThat(result.get("updatedCount").asInt()).isEqualTo(1);
        assertThat(result.get("changedGradeCount").asInt()).isEqualTo(2);
        assertThat(stored().get(second.getId()).getMarks()).isEqualByComparingTo(new BigDecimal("42.5"));
        assertThat(stored()).hasSize(3);
    }

    @Test
    void invalidMarksAreRejectedAndNothingIsSaved() throws Exception {
        List<List<Map<String, Object>>> invalid = List.of(
                List.of(row(first.getId(), 51, "GRADED")),           // above max
                List.of(row(first.getId(), -1, "GRADED")),           // negative
                List.of(row(first.getId(), null, "GRADED")),         // GRADED without marks
                List.of(row(first.getId(), 10, "ABSENT")),           // ABSENT with marks
                List.of(row(first.getId(), 0, "EXEMPTED")),          // EXEMPTED with marks
                List.of(row(first.getId(), 10, "PRESENT")),          // unknown status
                List.of(row(first.getId(), 10.555, "GRADED")));      // more than 2 decimals
        for (List<Map<String, Object>> rows : invalid) {
            putGrades(staff, draft.getId(), rows).andExpect(status().isBadRequest());
        }
        assertThat(stored()).isEmpty();
    }

    @Test
    void oneBadRowRejectsTheWholeBatchWithAllErrorsListed() throws Exception {
        Student elsewhere = student(otherSection);
        putGrades(staff, draft.getId(), List.of(
                row(first.getId(), 20, "GRADED"),
                row(second.getId(), 99, "GRADED"),
                row(elsewhere.getId(), 10, "GRADED"),
                row(first.getId(), 21, "GRADED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0]", containsString("max marks")))
                .andExpect(jsonPath("$.errors[1]", containsString("not in this subject offering's section")))
                .andExpect(jsonPath("$.errors[2]", containsString("more than once")));
        assertThat(stored()).isEmpty();
    }

    @Test
    void studentFromAnotherSectionOrUnknownStudentIsRejected() throws Exception {
        putGrades(staff, draft.getId(), List.of(row(student(otherSection).getId(), 10, "GRADED")))
                .andExpect(status().isBadRequest());
        putGrades(staff, draft.getId(), List.of(row(999_999_999L, 10, "GRADED")))
                .andExpect(status().isBadRequest());

        Student inactive = student(section);
        inactive.setActive(false);
        studentRepository.saveAndFlush(inactive);
        putGrades(staff, draft.getId(), List.of(row(inactive.getId(), 10, "GRADED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAssignedStaffCanEnterGrades() throws Exception {
        List<Map<String, Object>> rows = List.of(row(first.getId(), 10, "GRADED"));
        putGrades(studentLogin(section).user(), draft.getId(), rows).andExpect(status().isForbidden());
        putGrades(otherStaff, draft.getId(), rows).andExpect(status().isForbidden());
        putGrades(hod, draft.getId(), rows).andExpect(status().isForbidden());
        putGrades(staff, 999_999_999L, rows).andExpect(status().isNotFound());
        assertThat(stored()).isEmpty();
    }

    @Test
    void finalizedAssessmentGradesCannotChange() throws Exception {
        Assessment finalized = finalizedAssessment(draft.getSubjectOffering(), "CAT 2", Assessment.AssessmentType.CAT_2);
        putGrades(staff, finalized.getId(), List.of(row(first.getId(), 10, "GRADED")))
                .andExpect(status().isConflict());
    }

    @Test
    void emptyOrMalformedRequestIsBadRequest() throws Exception {
        putGrades(staff, draft.getId(), List.of()).andExpect(status().isBadRequest());
        putGrades(staff, draft.getId(), List.of(row(null, 10, "GRADED"))).andExpect(status().isBadRequest());
    }

    @Test
    void gradebookListsTheWholeClassIncludingUngradedStudents() throws Exception {
        student(otherSection);
        grade(draft, first, 30, StudentGrade.GradeStatus.GRADED);

        JsonNode gradebook = data(mvc.perform(get("/assessments/" + draft.getId() + "/grades").header("Authorization", token(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessment.id", is(draft.getId().intValue())))
                .andExpect(jsonPath("$.data.expectedStudentCount", is(3)))
                .andExpect(jsonPath("$.data.students[0].registerNumber", notNullValue())));

        Map<Long, JsonNode> rows = new java.util.HashMap<>();
        gradebook.get("students").forEach(r -> rows.put(r.get("studentId").asLong(), r));
        assertThat(rows).containsOnlyKeys(first.getId(), second.getId(), third.getId());
        assertThat(rows.get(first.getId()).get("gradeStatus").asText()).isEqualTo("GRADED");
        assertThat(rows.get(first.getId()).get("marks").decimalValue()).isEqualByComparingTo("30");
        assertThat(rows.get(second.getId()).get("gradeStatus").asText()).isEqualTo("NOT_GRADED");
        assertThat(rows.get(second.getId()).get("marks").isNull()).isTrue();

        mvc.perform(get("/assessments/" + draft.getId() + "/grades").header("Authorization", token(hod)))
                .andExpect(status().isOk());
        mvc.perform(get("/assessments/" + draft.getId() + "/grades").header("Authorization", token(otherStaff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assessments/" + draft.getId() + "/grades").header("Authorization", token(studentLogin(section).user())))
                .andExpect(status().isForbidden());
    }
}
