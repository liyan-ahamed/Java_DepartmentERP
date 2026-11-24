package com.java.erp.modules.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.support.PortfolioTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Student side: own drafts, edit/delete rules, submit and resubmit. */
class PortfolioStudentIntegrationTest extends PortfolioTestBase {

    @Test
    void studentCreatesDraftForOwnStudentRecord() throws Exception {
        // A student can never name the student: the field does not exist and is refused
        Map<String, Object> naming = completeEntry();
        naming.put("studentId", otherStudent.student().getId());
        create(student.user(), naming).andExpect(status().isBadRequest());

        create(student.user(), completeEntry())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.studentId", is(student.student().getId().intValue())))
                .andExpect(jsonPath("$.data.sectionId", is(section.getId().intValue())))
                .andExpect(jsonPath("$.data.activityType", is("HACKATHON")))
                .andExpect(jsonPath("$.data.level", is("NATIONAL")))
                .andExpect(jsonPath("$.data.submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.reviewedById").value(nullValue()));
    }

    @Test
    void draftNeedsOnlyTypeAndTitle() throws Exception {
        create(student.user(), Map.of("activityType", "INTERNSHIP", "title", "Backend intern"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.organization").value(nullValue()))
                .andExpect(jsonPath("$.data.startDate").value(nullValue()));
    }

    @Test
    void onlyStudentsCreateEntries() throws Exception {
        create(staff, completeEntry()).andExpect(status().isForbidden());
        create(hod, completeEntry()).andExpect(status().isForbidden());
        mvc.perform(get("/portfolio/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentListsOnlyOwnEntriesInEveryStatus() throws Exception {
        Long draftId = draft(student, completeEntry("WORKSHOP", "Docker workshop"));
        Long submittedId = submitted(student, completeEntry("CERTIFICATION", "AWS Cloud Practitioner"));
        draft(otherStudent);

        JsonNode page = data(mvc.perform(get("/portfolio/me").header("Authorization", token(student.user())))
                .andExpect(status().isOk()));
        assertThat(page.get("total_elements").asInt()).isEqualTo(2);
        assertThat(page.get("content").findValuesAsText("id")).containsExactlyInAnyOrder(draftId.toString(), submittedId.toString());

        mvc.perform(get("/portfolio/me").param("status", "SUBMITTED").header("Authorization", token(student.user())))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(submittedId.intValue())));
        mvc.perform(get("/portfolio/me").param("activityType", "WORKSHOP").header("Authorization", token(student.user())))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(draftId.intValue())));
        int year = java.time.LocalDate.now().minusMonths(2).getYear();
        mvc.perform(get("/portfolio/me").param("year", String.valueOf(year)).header("Authorization", token(student.user())))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
        mvc.perform(get("/portfolio/me").param("year", "2001").header("Authorization", token(student.user())))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void studentCannotSeeOrChangeAnotherStudentsEntry() throws Exception {
        Long othersDraft = draft(otherStudent);

        mvc.perform(get("/portfolio/entries/" + othersDraft).header("Authorization", token(student.user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
        update(student.user(), othersDraft, completeEntry()).andExpect(status().isForbidden());
        mvc.perform(delete("/portfolio/entries/" + othersDraft).header("Authorization", token(student.user())))
                .andExpect(status().isForbidden());
        submit(student.user(), othersDraft).andExpect(status().isForbidden());
        mvc.perform(get("/portfolio/entries/999999999").header("Authorization", token(student.user())))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentEditsAndDeletesDraft() throws Exception {
        Long id = draft(student);
        Map<String, Object> changed = completeEntry("COMPETITION", "Code Sprint 2026");
        changed.put("level", "STATE");

        update(student.user(), id, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Code Sprint 2026")))
                .andExpect(jsonPath("$.data.activityType", is("COMPETITION")))
                .andExpect(jsonPath("$.data.level", is("STATE")))
                .andExpect(jsonPath("$.data.status", is("DRAFT")));

        mvc.perform(delete("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(status().isOk());
        assertThat(portfolioRepository.findById(id)).isEmpty();
    }

    @Test
    void submitMovesDraftToSubmittedAndLocksIt() throws Exception {
        Long id = draft(student);

        submit(student.user(), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.data.submittedAt", notNullValue()));

        update(student.user(), id, completeEntry()).andExpect(status().isConflict());
        mvc.perform(delete("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(status().isConflict());
        submit(student.user(), id).andExpect(status().isConflict());
        assertThat(portfolioRepository.findById(id)).get()
                .extracting(PortfolioEntry::getStatus).isEqualTo(PortfolioEntry.Status.SUBMITTED);
    }

    @Test
    void rejectedEntryCanBeEditedAndResubmitted() throws Exception {
        Long id = submitted(student);
        reject(staff, id, "Please add the certificate link.").andExpect(status().isOk());

        Map<String, Object> fixed = completeEntry();
        fixed.put("evidenceUrl", "https://example.org/certificates/123");
        update(student.user(), id, fixed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED"))) // stays rejected until resubmitted
                .andExpect(jsonPath("$.data.reviewNote", is("Please add the certificate link.")));

        submit(student.user(), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")));
        verify(staff, id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VERIFIED")));
    }

    @Test
    void rejectedEntryCanBeDeleted() throws Exception {
        Long id = submitted(student);
        reject(staff, id, "This is not a technical activity.").andExpect(status().isOk());

        mvc.perform(delete("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(status().isOk());
        assertThat(portfolioRepository.findById(id)).isEmpty();
    }

    @Test
    void verifiedEntryIsImmutableForStudent() throws Exception {
        Long id = submitted(student);
        verify(staff, id, "Certificate checked").andExpect(status().isOk());

        update(student.user(), id, completeEntry()).andExpect(status().isConflict());
        mvc.perform(delete("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(status().isConflict());
        submit(student.user(), id).andExpect(status().isConflict());

        mvc.perform(get("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VERIFIED")))
                .andExpect(jsonPath("$.data.reviewNote", is("Certificate checked")))
                .andExpect(jsonPath("$.data.reviewedById", is(staff.getId().intValue())));
    }

    @Test
    void studentSummaryCountsOwnEntries() throws Exception {
        draft(student, completeEntry("INTERNSHIP", "Intern"));
        submitted(student, completeEntry("COMPETITION", "Contest"));
        Long verified = submitted(student, completeEntry("HACKATHON", "SIH"));
        Long rejected = submitted(student, completeEntry("HACKATHON", "Another hackathon"));
        verify(staff, verified, null).andExpect(status().isOk());
        reject(staff, rejected, "Missing the team certificate").andExpect(status().isOk());
        draft(otherStudent);

        mvc.perform(get("/portfolio/me/summary").header("Authorization", token(student.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEntries", is(4)))
                .andExpect(jsonPath("$.data.draft", is(1)))
                .andExpect(jsonPath("$.data.submitted", is(1)))
                .andExpect(jsonPath("$.data.verified", is(1)))
                .andExpect(jsonPath("$.data.rejected", is(1)))
                .andExpect(jsonPath("$.data.byActivityType.HACKATHON", is(2)))
                .andExpect(jsonPath("$.data.byActivityType.INTERNSHIP", is(1)))
                .andExpect(jsonPath("$.data.byActivityType.WORKSHOP", is(0)));
    }
}
