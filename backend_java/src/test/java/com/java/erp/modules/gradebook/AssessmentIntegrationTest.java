package com.java.erp.modules.gradebook;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssessmentIntegrationTest extends GradebookTestBase {

    private Map<String, Object> details(Object maxMarks, Object passMarks) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "CAT 1");
        body.put("assessmentType", "CAT_1");
        body.put("maxMarks", maxMarks);
        body.put("passMarks", passMarks);
        body.put("assessmentDate", "2026-09-15");
        return body;
    }

    private ResultActions create(User user, SubjectOffering offering, Map<String, Object> body) throws Exception {
        body.put("subjectOfferingId", offering.getId());
        return mvc.perform(post("/assessments").header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions update(User user, Long id, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/assessments/" + id).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void staffCreatesDraftAssessmentForOwnOffering() throws Exception {
        SubjectOffering offering = theoryOffering();
        create(staff, offering, details(50, 25))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.subjectOfferingId", is(offering.getId().intValue())))
                .andExpect(jsonPath("$.data.createdById", is(staff.getId().intValue())))
                .andExpect(jsonPath("$.data.finalizedAt").doesNotExist());

        // Several assessments may share a type (Assignment 1, Assignment 2)
        Map<String, Object> assignment = details(10, null);
        assignment.put("assessmentType", "ASSIGNMENT");
        create(staff, offering, new HashMap<>(assignment)).andExpect(status().isCreated());
        create(staff, offering, new HashMap<>(assignment)).andExpect(status().isCreated());
    }

    @Test
    void staffCannotCreateForSomeoneElsesOfferingAndHodOrStudentCannotCreate() throws Exception {
        SubjectOffering theirs = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        create(staff, theirs, details(50, 25)).andExpect(status().isForbidden());
        create(hod, theirs, details(50, 25)).andExpect(status().isForbidden());
        create(studentLogin(otherSection).user(), theirs, details(50, 25)).andExpect(status().isForbidden());
        assertThat(assessmentRepository.existsBySubjectOfferingId(theirs.getId())).isFalse();
    }

    @Test
    void invalidMarksSettingsAreBadRequest() throws Exception {
        SubjectOffering offering = theoryOffering();
        create(staff, offering, details(0, null)).andExpect(status().isBadRequest());
        create(staff, offering, details(-10, null)).andExpect(status().isBadRequest());
        create(staff, offering, details(null, null)).andExpect(status().isBadRequest());
        create(staff, offering, details(50, 60)).andExpect(status().isBadRequest());
        create(staff, offering, details(50, -1)).andExpect(status().isBadRequest());

        Map<String, Object> badWeightage = details(50, 25);
        badWeightage.put("weightage", 150);
        create(staff, offering, badWeightage).andExpect(status().isBadRequest());

        Map<String, Object> badType = details(50, 25);
        badType.put("assessmentType", "EXAM");
        create(staff, offering, badType).andExpect(status().isBadRequest());

        Map<String, Object> badDate = details(50, 25);
        badDate.put("assessmentDate", "15-09-2026");
        create(staff, offering, badDate).andExpect(status().isBadRequest());

        Map<String, Object> noTitle = details(50, 25);
        noTitle.put("title", " ");
        create(staff, offering, noTitle).andExpect(status().isBadRequest());
    }

    @Test
    void inactiveOfferingCannotGetAssessments() throws Exception {
        SubjectOffering offering = theoryOffering();
        offering.setActive(false);
        offeringRepository.saveAndFlush(offering);
        create(staff, offering, details(50, 25)).andExpect(status().isBadRequest());
    }

    @Test
    void draftCanBeEditedButNotByOtherStaff() throws Exception {
        Assessment draft = draftAssessment(theoryOffering());
        Map<String, Object> changed = details(60, 30);
        changed.put("title", "CAT 1 (revised)");

        update(otherStaff, draft.getId(), changed).andExpect(status().isForbidden());
        update(hod, draft.getId(), changed).andExpect(status().isForbidden());
        update(staff, draft.getId(), changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("CAT 1 (revised)")))
                .andExpect(jsonPath("$.data.maxMarks", is(60)));
        update(staff, 999_999_999L, changed).andExpect(status().isNotFound());
    }

    @Test
    void maxMarksCannotDropBelowEnteredMarks() throws Exception {
        Assessment draft = draftAssessment(theoryOffering());
        grade(draft, student(section), 40, StudentGrade.GradeStatus.GRADED);
        update(staff, draft.getId(), details(30, 15)).andExpect(status().isBadRequest());
    }

    @Test
    void finalizedAssessmentCannotBeEditedOrDeleted() throws Exception {
        Assessment finalized = finalizedAssessment(theoryOffering(), "CAT 1", Assessment.AssessmentType.CAT_1);
        update(staff, finalized.getId(), details(50, 25)).andExpect(status().isConflict());
        mvc.perform(delete("/assessments/" + finalized.getId()).header("Authorization", token(staff)))
                .andExpect(status().isConflict());
        assertThat(assessmentRepository.findById(finalized.getId())).isPresent();
    }

    @Test
    void draftCanBeDeletedWithItsGrades() throws Exception {
        Assessment draft = draftAssessment(theoryOffering());
        grade(draft, student(section), 20, StudentGrade.GradeStatus.GRADED);

        mvc.perform(delete("/assessments/" + draft.getId()).header("Authorization", token(otherStaff)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/assessments/" + draft.getId()).header("Authorization", token(staff)))
                .andExpect(status().isOk());
        assertThat(assessmentRepository.findById(draft.getId())).isEmpty();
        assertThat(gradeRepository.findByAssessmentId(draft.getId())).isEmpty();
    }

    @Test
    void ownerFinalizesOnceAndOthersCannot() throws Exception {
        Assessment draft = draftAssessment(theoryOffering());

        mvc.perform(post("/assessments/" + draft.getId() + "/finalize").header("Authorization", token(otherStaff)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/assessments/" + draft.getId() + "/finalize").header("Authorization", token(hod)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/assessments/" + draft.getId() + "/finalize").header("Authorization", token(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("FINALIZED")))
                .andExpect(jsonPath("$.data.finalizedAt", notNullValue()));
        mvc.perform(post("/assessments/" + draft.getId() + "/finalize").header("Authorization", token(staff)))
                .andExpect(status().isConflict());

        Assessment stored = assessmentRepository.findById(draft.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Assessment.Status.FINALIZED);
        assertThat(stored.getFinalizedAt()).isNotNull();
    }

    @Test
    void listIsScopedAndFilterable() throws Exception {
        SubjectOffering mine = theoryOffering();
        SubjectOffering theirs = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        draftAssessment(mine);
        finalizedAssessment(mine, "Assignment 1", Assessment.AssessmentType.ASSIGNMENT);
        Assessment other = draftAssessment(theirs);

        mvc.perform(get("/assessments").param("sectionId", otherSection.getId().toString()).header("Authorization", token(staff)))
                .andExpect(jsonPath("$.data.total_elements", is(0)));
        mvc.perform(get("/assessments").param("subjectOfferingId", mine.getId().toString())
                        .param("status", "FINALIZED").header("Authorization", token(staff)))
                .andExpect(jsonPath("$.data.total_elements", is(1)))
                .andExpect(jsonPath("$.data.content[0].assessmentType", is("ASSIGNMENT")));
        mvc.perform(get("/assessments/" + other.getId()).header("Authorization", token(staff)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/assessments").param("subjectOfferingId", theirs.getId().toString()).header("Authorization", token(hod)))
                .andExpect(jsonPath("$.data.total_elements", is(1)));
        mvc.perform(get("/assessments").param("status", "OPEN").header("Authorization", token(hod)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/assessments").header("Authorization", token(studentLogin(section).user())))
                .andExpect(status().isForbidden());
    }
}
