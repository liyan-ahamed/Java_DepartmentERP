package com.java.erp.modules.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.GradebookTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubjectOfferingIntegrationTest extends GradebookTestBase {

    private Map<String, Object> body(Long subjectId, Long batchId, Long sectionId, Long staffId) {
        Map<String, Object> body = new HashMap<>();
        body.put("subjectId", subjectId);
        body.put("batchId", batchId);
        body.put("sectionId", sectionId);
        body.put("staffId", staffId);
        body.put("academicYear", ACADEMIC_YEAR);
        body.put("semester", 3);
        return body;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/subject-offerings").header("Authorization", token(hod))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void hodCreatesValidOfferingWithPendingInternalRecords() throws Exception {
        Subject subject = subject(Subject.SubjectType.THEORY);
        student(section);
        student(section);
        student(otherSection);

        JsonNode created = data(create(body(subject.getId(), batch.getId(), section.getId(), staff.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectCode", is(subject.getCode())))
                .andExpect(jsonPath("$.data.staffId", is(staff.getId().intValue())))
                .andExpect(jsonPath("$.data.active", is(true))));

        assertThat(internalMarkRepository.findBySubjectOfferingId(created.get("id").asLong())).hasSize(2);
    }

    @Test
    void invalidReferencesAreRejected() throws Exception {
        Subject subject = subject(Subject.SubjectType.THEORY);
        User studentUser = studentLogin(section).user();
        User inactiveStaff = createUser("ROLE_STAFF", false);
        long missing = 999_999_999L;

        create(body(missing, batch.getId(), section.getId(), staff.getId())).andExpect(status().isBadRequest());
        create(body(subject.getId(), missing, section.getId(), staff.getId())).andExpect(status().isBadRequest());
        create(body(subject.getId(), batch.getId(), missing, staff.getId())).andExpect(status().isBadRequest());
        create(body(subject.getId(), batch.getId(), section.getId(), missing)).andExpect(status().isBadRequest());
        // staff must be an active STAFF user
        create(body(subject.getId(), batch.getId(), section.getId(), studentUser.getId())).andExpect(status().isBadRequest());
        create(body(subject.getId(), batch.getId(), section.getId(), hod.getId())).andExpect(status().isBadRequest());
        create(body(subject.getId(), batch.getId(), section.getId(), inactiveStaff.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void academicContextIsValidated() throws Exception {
        Subject subject = subject(Subject.SubjectType.THEORY);
        var otherBatch = batchRepository.save(new com.java.erp.modules.system.entity.Batch("GX-" + unique(), 2024, 2028, (short) 3));

        // section from another batch
        create(body(subject.getId(), otherBatch.getId(), section.getId(), staff.getId())).andExpect(status().isBadRequest());

        Map<String, Object> badYear = body(subject.getId(), batch.getId(), section.getId(), staff.getId());
        badYear.put("academicYear", "2026-2028");
        create(badYear).andExpect(status().isBadRequest());
        badYear.put("academicYear", "26-27");
        create(badYear).andExpect(status().isBadRequest());

        Map<String, Object> badSemester = body(subject.getId(), batch.getId(), section.getId(), staff.getId());
        badSemester.put("semester", 9);
        create(badSemester).andExpect(status().isBadRequest());

        subject.setActive(false);
        subjectRepository.saveAndFlush(subject);
        create(body(subject.getId(), batch.getId(), section.getId(), staff.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateActiveOfferingIsConflict() throws Exception {
        Subject subject = subject(Subject.SubjectType.THEORY);
        create(body(subject.getId(), batch.getId(), section.getId(), staff.getId())).andExpect(status().isCreated());
        create(body(subject.getId(), batch.getId(), section.getId(), otherStaff.getId())).andExpect(status().isConflict());

        Map<String, Object> inactive = body(subject.getId(), batch.getId(), section.getId(), otherStaff.getId());
        inactive.put("active", false);
        create(inactive).andExpect(status().isCreated());
    }

    @Test
    void staffSeeOnlyTheirOwnOfferingsAndHodSeesAll() throws Exception {
        SubjectOffering mine = theoryOffering();
        SubjectOffering theirs = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);

        mvc.perform(get("/subject-offerings").param("sectionId", section.getId().toString())
                        .header("Authorization", token(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements", is(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(mine.getId().intValue())));
        // A staffId filter cannot widen a STAFF member's view
        mvc.perform(get("/subject-offerings").param("staffId", otherStaff.getId().toString())
                        .header("Authorization", token(staff)))
                .andExpect(jsonPath("$.data.content[?(@.id == " + theirs.getId() + ")]").isEmpty());
        mvc.perform(get("/subject-offerings/" + theirs.getId()).header("Authorization", token(staff)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/subject-offerings").param("batchId", batch.getId().toString()).header("Authorization", token(hod)))
                .andExpect(jsonPath("$.data.total_elements", is(2)));
        mvc.perform(get("/subject-offerings/" + theirs.getId()).header("Authorization", token(hod)))
                .andExpect(status().isOk());
        mvc.perform(get("/subject-offerings").header("Authorization", token(studentLogin(section).user())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/subject-offerings").header("Authorization", token(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(mine.getSubject().getId(), batch.getId(), otherSection.getId(), staff.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanBeReassignedButContextIsFixedOnceAssessmentsExist() throws Exception {
        SubjectOffering offering = theoryOffering();
        draftAssessment(offering);

        Map<String, Object> reassigned = body(offering.getSubject().getId(), batch.getId(), section.getId(), otherStaff.getId());
        mvc.perform(put("/subject-offerings/" + offering.getId()).header("Authorization", token(hod))
                        .contentType(MediaType.APPLICATION_JSON).content(json(reassigned)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffId", is(otherStaff.getId().intValue())));

        Map<String, Object> moved = body(offering.getSubject().getId(), batch.getId(), otherSection.getId(), otherStaff.getId());
        mvc.perform(put("/subject-offerings/" + offering.getId()).header("Authorization", token(hod))
                        .contentType(MediaType.APPLICATION_JSON).content(json(moved)))
                .andExpect(status().isConflict());
    }
}
