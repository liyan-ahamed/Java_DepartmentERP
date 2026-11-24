package com.java.erp.modules.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.PortfolioTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** STAFF (sections they teach) and HOD (everyone) review rules. */
/** Uses GET /portfolio/summary, so Portfolio Analytics is switched on here (it is off by default). */
@org.springframework.test.context.TestPropertySource(properties = "features.portfolio-analytics.enabled=true")
class PortfolioReviewIntegrationTest extends PortfolioTestBase {

    private JsonNode queue(User user, String... params) throws Exception {
        var request = get("/portfolio/review").header("Authorization", token(user));
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return data(mvc.perform(request).andExpect(status().isOk()));
    }

    @Test
    void staffQueueShowsSubmittedEntriesOfTaughtSectionsOnly() throws Exception {
        Long mine = submitted(student);
        submitted(otherStudent);
        draft(student); // drafts never reach the queue

        JsonNode page = queue(staff);
        assertThat(page.get("total_elements").asInt()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("id").asLong()).isEqualTo(mine);
        assertThat(page.get("content").get(0).get("status").asText()).isEqualTo("SUBMITTED");

        // Filtering on a section outside their scope shows nothing rather than other students
        assertThat(queue(staff, "sectionId", otherSection.getId().toString()).get("total_elements").asInt()).isZero();
    }

    @Test
    void staffWithoutActiveOfferingsSeesNothing() throws Exception {
        submitted(student);
        User newcomer = createUser("ROLE_STAFF", true);
        assertThat(queue(newcomer).get("total_elements").asInt()).isZero();

        SubjectOffering inactive = theoryOffering();
        inactive.setStaff(newcomer);
        inactive.setActive(false);
        offeringRepository.saveAndFlush(inactive);
        assertThat(queue(newcomer).get("total_elements").asInt()).isZero();
    }

    @Test
    void queueFiltersWork() throws Exception {
        Map<String, Object> hackathon = completeEntry("HACKATHON", "Smart India Hackathon");
        Map<String, Object> certification = completeEntry("CERTIFICATION", "AWS Cloud Practitioner");
        certification.put("level", "ONLINE");
        Long h = submitted(student, hackathon);
        Long c = submitted(student, certification);

        assertThat(queue(staff, "activityType", "CERTIFICATION").get("content").findValuesAsText("id")).containsExactly(c.toString());
        assertThat(queue(staff, "level", "NATIONAL").get("content").findValuesAsText("id")).containsExactly(h.toString());
        assertThat(queue(staff, "search", "aws").get("content").findValuesAsText("id")).containsExactly(c.toString());
        assertThat(queue(staff, "search", student.student().getRegisterNumber().toLowerCase()).get("total_elements").asInt()).isEqualTo(2);
        assertThat(queue(hod, "batchId", batch.getId().toString()).get("total_elements").asInt()).isEqualTo(2);
        assertThat(queue(staff, "status", "VERIFIED").get("total_elements").asInt()).isZero();
        mvc.perform(get("/portfolio/review").param("status", "DRAFT").header("Authorization", token(staff)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffVerifiesAuthorizedEntry() throws Exception {
        Long id = submitted(student);

        verify(staff, id, "Certificate checked")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VERIFIED")))
                .andExpect(jsonPath("$.data.reviewedById", is(staff.getId().intValue())))
                .andExpect(jsonPath("$.data.reviewedByName", is(staff.getName())))
                .andExpect(jsonPath("$.data.reviewedAt", notNullValue()))
                .andExpect(jsonPath("$.data.reviewNote", is("Certificate checked")));
        assertThat(queue(staff).get("total_elements").asInt()).isZero();
        assertThat(queue(staff, "status", "VERIFIED").get("total_elements").asInt()).isEqualTo(1);
    }

    @Test
    void verifyWorksWithoutBodyAndAcceptsVerificationNoteAlias() throws Exception {
        Long first = submitted(student);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/portfolio/entries/" + first + "/verify").header("Authorization", token(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewNote").value(nullValue()));

        Long second = submitted(student);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/portfolio/entries/" + second + "/reject").header("Authorization", token(staff))
                        .contentType("application/json").content(json(Map.of("verificationNote", "Please attach the certificate."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewNote", is("Please attach the certificate.")));
    }

    @Test
    void staffRejectsWithNote() throws Exception {
        Long id = submitted(student);

        reject(staff, id, "  Please add the certificate link.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")))
                .andExpect(jsonPath("$.data.reviewNote", is("Please add the certificate link.")))
                .andExpect(jsonPath("$.data.reviewedById", is(staff.getId().intValue())));
    }

    @Test
    void rejectionWithoutMeaningfulNoteIsRefused() throws Exception {
        Long id = submitted(student);

        reject(staff, id, null).andExpect(status().isBadRequest());
        reject(staff, id, "   ").andExpect(status().isBadRequest());
        reject(staff, id, "no").andExpect(status().isBadRequest());
        reject(staff, id, "  bad      ").andExpect(status().isBadRequest());
        mvc.perform(get("/portfolio/entries/" + id).header("Authorization", token(staff)))
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")));
    }

    @Test
    void staffCannotReviewStudentsOutsideTheirSections() throws Exception {
        Long id = submitted(otherStudent);

        verify(staff, id, null).andExpect(status().isForbidden());
        reject(staff, id, "Not your student at all").andExpect(status().isForbidden());
        mvc.perform(get("/portfolio/entries/" + id).header("Authorization", token(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/portfolio/students/" + otherStudent.student().getId()).header("Authorization", token(staff)))
                .andExpect(status().isForbidden());

        // The staff member who teaches that section may review it
        verify(otherStaff, id, null).andExpect(status().isOk());
    }

    @Test
    void staffAndHodCannotSeeDrafts() throws Exception {
        Long id = draft(student);
        mvc.perform(get("/portfolio/entries/" + id).header("Authorization", token(staff))).andExpect(status().isNotFound());
        mvc.perform(get("/portfolio/entries/" + id).header("Authorization", token(hod))).andExpect(status().isNotFound());
    }

    @Test
    void studentsCannotUseReviewEndpoints() throws Exception {
        Long id = submitted(otherStudent);
        mvc.perform(get("/portfolio/review").header("Authorization", token(student.user()))).andExpect(status().isForbidden());
        verify(student.user(), id, null).andExpect(status().isForbidden());
        reject(otherStudent.user(), id, "Rejecting my own entry").andExpect(status().isForbidden());
        mvc.perform(get("/portfolio/summary").header("Authorization", token(student.user()))).andExpect(status().isForbidden());
        mvc.perform(get("/portfolio/students/" + otherStudent.student().getId()).header("Authorization", token(student.user())))
                .andExpect(status().isForbidden());
    }

    @Test
    void facultyCannotEditOrCreateStudentEntries() throws Exception {
        Long id = submitted(student);
        update(staff, id, completeEntry()).andExpect(status().isForbidden());
        update(hod, id, completeEntry()).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/portfolio/entries/" + id).header("Authorization", token(hod))).andExpect(status().isForbidden());
        submit(hod, id).andExpect(status().isForbidden());
    }

    @Test
    void hodReviewsAnyStudent() throws Exception {
        Long a = submitted(student);
        Long b = submitted(otherStudent);

        assertThat(queue(hod).get("total_elements").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(queue(hod, "batchId", batch.getId().toString()).get("content").findValuesAsText("id"))
                .containsExactly(a.toString(), b.toString()); // oldest submission first
        verify(hod, a, null).andExpect(status().isOk());
        reject(hod, b, "Please describe your role in the project.").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewedById", is(hod.getId().intValue())));
    }

    @Test
    void verifiedPortfolioShowsVerifiedEntriesOnly() throws Exception {
        Long verified = submitted(student, completeEntry("HACKATHON", "SIH 2026"));
        Long rejected = submitted(student, completeEntry("WORKSHOP", "Docker workshop"));
        submitted(student, completeEntry("COMPETITION", "Code sprint"));
        draft(student);
        verify(staff, verified, null).andExpect(status().isOk());
        reject(staff, rejected, "Add the certificate please").andExpect(status().isOk());

        for (User viewer : new User[]{hod, staff}) {
            mvc.perform(get("/portfolio/students/" + student.student().getId()).header("Authorization", token(viewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.studentId", is(student.student().getId().intValue())))
                    .andExpect(jsonPath("$.data.verifiedCount", is(1)))
                    .andExpect(jsonPath("$.data.entries", hasSize(1)))
                    .andExpect(jsonPath("$.data.entries[0].id", is(verified.intValue())))
                    .andExpect(jsonPath("$.data.verifiedByActivityType.HACKATHON", is(1)))
                    .andExpect(jsonPath("$.data.verifiedByActivityType.WORKSHOP", is(0)));
        }
        mvc.perform(get("/portfolio/students/999999999").header("Authorization", token(hod))).andExpect(status().isNotFound());
    }
}
