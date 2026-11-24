package com.java.erp.modules.gradebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.entity.StudentInternalMark;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalMarksIntegrationTest extends GradebookTestBase {

    private static final String PENDING = "PENDING_CALCULATION_CONFIGURATION";

    private JsonNode forOffering(SubjectOffering offering) throws Exception {
        return data(mvc.perform(get("/subject-offerings/" + offering.getId() + "/internal-marks")
                .header("Authorization", token(staff))).andExpect(status().isOk()));
    }

    private static void assertNoTotals(JsonNode entry) {
        assertThat(entry.get("theoryComponentTotal").isNull()).isTrue();
        assertThat(entry.get("practicalComponentTotal").isNull()).isTrue();
        assertThat(entry.get("totalInternalMarks").isNull()).isTrue();
        assertThat(entry.get("calculationStatus").asText()).isEqualTo(PENDING);
        assertThat(entry.get("calculatedAt").isNull()).isTrue();
    }

    @Test
    void theoryOfferingHasPendingRecordsWithoutPracticalComponent() throws Exception {
        Student student = student(section);
        SubjectOffering offering = theoryOffering();
        Assessment cat1 = finalizedAssessment(offering, "CAT 1", Assessment.AssessmentType.CAT_1);
        Assessment a1 = finalizedAssessment(offering, "Assignment 1", Assessment.AssessmentType.ASSIGNMENT);
        Assessment a2 = finalizedAssessment(offering, "Assignment 2", Assessment.AssessmentType.ASSIGNMENT);
        draftAssessment(offering); // CAT 1 draft: must not appear
        grade(cat1, student, 42, StudentGrade.GradeStatus.GRADED);
        grade(a1, student, null, StudentGrade.GradeStatus.ABSENT);

        JsonNode view = forOffering(offering);

        assertThat(view.get("offering").get("subjectType").asText()).isEqualTo("THEORY");
        assertThat(view.get("practicalComponentApplicable").asBoolean()).isFalse();
        assertThat(view.get("students")).hasSize(1);
        JsonNode entry = view.get("students").get(0);
        assertNoTotals(entry);

        JsonNode byType = entry.get("finalizedAssessmentsByType");
        assertThat(byType.get("CAT_1")).hasSize(1);
        assertThat(byType.get("CAT_1").get(0).get("marks").decimalValue()).isEqualByComparingTo("42");
        assertThat(byType.get("ASSIGNMENT")).hasSize(2);
        assertThat(byType.get("ASSIGNMENT").get(0).get("gradeStatus").asText()).isEqualTo("ABSENT");
        assertThat(byType.get("ASSIGNMENT").get(1).get("assessmentId").asLong()).isEqualTo(a2.getId());
        assertThat(byType.get("ASSIGNMENT").get(1).get("gradeStatus").asText()).isEqualTo("NOT_GRADED");

        StudentInternalMark record = internalMarkRepository.findBySubjectOfferingId(offering.getId()).get(0);
        assertThat(record.getCalculationStatus()).isEqualTo(StudentInternalMark.CalculationStatus.PENDING_CALCULATION_CONFIGURATION);
        assertThat(record.getTheoryComponentTotal()).isNull();
        assertThat(record.getTotalInternalMarks()).isNull();
    }

    @Test
    void theoryCumPracticalOfferingExposesTheoryPracticalAndTotalAsNull() throws Exception {
        Student student = student(section);
        SubjectOffering offering = offering(subject(Subject.SubjectType.THEORY_CUM_PRACTICAL), section, staff);
        grade(finalizedAssessment(offering, "Lab 1", Assessment.AssessmentType.PRACTICAL), student, 20, StudentGrade.GradeStatus.GRADED);
        finalizedAssessment(offering, "Record", Assessment.AssessmentType.RECORD);

        JsonNode view = forOffering(offering);

        assertThat(view.get("offering").get("subjectType").asText()).isEqualTo("THEORY_CUM_PRACTICAL");
        assertThat(view.get("practicalComponentApplicable").asBoolean()).isTrue();
        JsonNode entry = view.get("students").get(0);
        assertThat(entry.has("theoryComponentTotal")).isTrue();
        assertThat(entry.has("practicalComponentTotal")).isTrue();
        assertThat(entry.has("totalInternalMarks")).isTrue();
        assertNoTotals(entry);
        assertThat(entry.get("finalizedAssessmentsByType").get("PRACTICAL")).hasSize(1);
        assertThat(entry.get("finalizedAssessmentsByType").get("RECORD")).hasSize(1);
    }

    @Test
    void finalizingDoesNotCalculateAnything() throws Exception {
        Student student = student(section);
        SubjectOffering offering = theoryOffering();
        Assessment cat = draftAssessment(offering);
        grade(cat, student, 45, StudentGrade.GradeStatus.GRADED);

        mvc.perform(post("/assessments/" + cat.getId() + "/finalize").header("Authorization", token(staff)))
                .andExpect(status().isOk());

        assertThat(internalMarkRepository.findBySubjectOfferingId(offering.getId())).singleElement().satisfies(m -> {
            assertThat(m.getCalculationStatus()).isEqualTo(StudentInternalMark.CalculationStatus.PENDING_CALCULATION_CONFIGURATION);
            assertThat(m.getTheoryComponentTotal()).isNull();
            assertThat(m.getPracticalComponentTotal()).isNull();
            assertThat(m.getTotalInternalMarks()).isNull();
            assertThat(m.getCalculatedAt()).isNull();
        });
    }

    @Test
    void finalizingCreatesRecordsForStudentsWhoJoinedLater() throws Exception {
        SubjectOffering offering = theoryOffering();
        Student late = student(section);
        Assessment cat = draftAssessment(offering);
        assertThat(internalMarkRepository.findBySubjectOfferingId(offering.getId())).isEmpty();
        // Finalization needs a mark or an explicit absent status for every student of the class.
        grade(cat, late, null, StudentGrade.GradeStatus.ABSENT);

        mvc.perform(post("/assessments/" + cat.getId() + "/finalize").header("Authorization", token(staff)))
                .andExpect(status().isOk());

        assertThat(internalMarkRepository.findBySubjectOfferingId(offering.getId()))
                .extracting(m -> m.getStudent().getId()).containsExactly(late.getId());
    }

    @Test
    void theorySubjectCannotStoreAPracticalComponent() {
        Student student = student(section);
        SubjectOffering offering = theoryOffering();
        StudentInternalMark record = internalMarkRepository.findBySubjectOfferingId(offering.getId()).stream()
                .filter(m -> m.getStudent().getId().equals(student.getId())).findFirst().orElseThrow();
        record.setCalculationStatus(StudentInternalMark.CalculationStatus.CALCULATED);
        record.setCalculatedAt(java.time.LocalDateTime.now());
        record.setPracticalComponentTotal(new BigDecimal("10"));

        assertThatThrownBy(() -> internalMarkRepository.saveAndFlush(record))
                .satisfies(ex -> assertThat(Stream.iterate(ex, Objects::nonNull, Throwable::getCause))
                        .anyMatch(cause -> cause instanceof IllegalStateException
                                && cause.getMessage().contains("no practical component")));
    }

    @Test
    void studentSeesOnlyTheirOwnInternalData() throws Exception {
        StudentLogin me = studentLogin(section);
        StudentLogin classmate = studentLogin(section);
        StudentLogin elsewhere = studentLogin(otherSection);
        SubjectOffering mine = theoryOffering();
        offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        Assessment cat = finalizedAssessment(mine, "CAT 1", Assessment.AssessmentType.CAT_1);
        grade(cat, me.student(), 38, StudentGrade.GradeStatus.GRADED);
        grade(cat, classmate.student(), 12, StudentGrade.GradeStatus.GRADED);

        JsonNode result = data(mvc.perform(get("/internal-marks/me").header("Authorization", token(me.user())))
                .andExpect(status().isOk()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("offering").get("id").asLong()).isEqualTo(mine.getId());
        assertThat(result.get(0).get("students")).hasSize(1);
        JsonNode entry = result.get(0).get("students").get(0);
        assertThat(entry.get("studentId").asLong()).isEqualTo(me.student().getId());
        assertNoTotals(entry);
        assertThat(entry.get("finalizedAssessmentsByType").get("CAT_1").get(0).get("marks").decimalValue())
                .isEqualByComparingTo("38");
        assertThat(result.toString()).doesNotContain(classmate.student().getRegisterNumber())
                .doesNotContain(elsewhere.student().getRegisterNumber());

        mvc.perform(get("/subject-offerings/" + mine.getId() + "/internal-marks").header("Authorization", token(me.user())))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffOnlySeeTheirOwnOfferingsAndHodSeesAll() throws Exception {
        SubjectOffering theirs = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        mvc.perform(get("/subject-offerings/" + theirs.getId() + "/internal-marks").header("Authorization", token(staff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/subject-offerings/" + theirs.getId() + "/internal-marks").header("Authorization", token(hod)))
                .andExpect(status().isOk());
        mvc.perform(get("/internal-marks/me").header("Authorization", token(staff)))
                .andExpect(status().isForbidden());
    }
}
