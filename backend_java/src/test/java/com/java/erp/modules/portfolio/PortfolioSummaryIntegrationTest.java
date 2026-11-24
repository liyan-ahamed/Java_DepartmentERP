package com.java.erp.modules.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.support.PortfolioTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses GET /portfolio/summary, so Portfolio Analytics is switched on here (it is off by default). */
@org.springframework.test.context.TestPropertySource(properties = "features.portfolio-analytics.enabled=true")
class PortfolioSummaryIntegrationTest extends PortfolioTestBase {

    private JsonNode summary(User user) throws Exception {
        return data(mvc.perform(get("/portfolio/summary").header("Authorization", token(user))).andExpect(status().isOk()));
    }

    private static JsonNode find(JsonNode array, String idField, Long id) {
        for (JsonNode node : array) {
            if (node.get(idField).asLong() == id) return node;
        }
        return null;
    }

    /**
     * section (taught by staff): 2 verified (HACKATHON/NATIONAL, CERTIFICATION/ONLINE), 1 rejected, 1 submitted, 1 draft.
     * otherSection: 1 verified HACKATHON/STATE, 1 submitted.
     */
    private void seed() throws Exception {
        Map<String, Object> certification = completeEntry("CERTIFICATION", "AWS Cloud Practitioner");
        certification.put("level", "ONLINE");
        Map<String, Object> stateHackathon = completeEntry("HACKATHON", "State hackathon");
        stateHackathon.put("level", "STATE");

        verify(staff, submitted(student, completeEntry("HACKATHON", "SIH 2026")), null).andExpect(status().isOk());
        verify(staff, submitted(student, certification), null).andExpect(status().isOk());
        reject(staff, submitted(student, completeEntry("WORKSHOP", "Docker workshop")), "Add the certificate please")
                .andExpect(status().isOk());
        submitted(student, completeEntry("COMPETITION", "Code sprint"));
        draft(student, completeEntry("INTERNSHIP", "Backend intern"));

        verify(otherStaff, submitted(otherStudent, stateHackathon), null).andExpect(status().isOk());
        submitted(otherStudent, completeEntry("CONFERENCE", "IEEE conference"));
    }

    @Test
    void hodSummaryCountsTheDepartment() throws Exception {
        JsonNode before = summary(hod);
        seed();
        JsonNode after = summary(hod);

        assertThat(after.get("scope").asText()).isEqualTo("DEPARTMENT");
        assertThat(after.get("verified").asLong() - before.get("verified").asLong()).isEqualTo(3);
        assertThat(after.get("pendingReview").asLong() - before.get("pendingReview").asLong()).isEqualTo(2);
        assertThat(after.get("rejected").asLong() - before.get("rejected").asLong()).isEqualTo(1);
        assertThat(after.get("studentsWithVerifiedEntries").asLong() - before.get("studentsWithVerifiedEntries").asLong()).isEqualTo(2);
        assertThat(delta(before, after, "verifiedByActivityType", "HACKATHON")).isEqualTo(2);
        assertThat(delta(before, after, "verifiedByActivityType", "CERTIFICATION")).isEqualTo(1);
        assertThat(delta(before, after, "verifiedByActivityType", "WORKSHOP")).isZero();
        assertThat(delta(before, after, "verifiedByActivityType", "INTERNSHIP")).isZero(); // drafts never count
        assertThat(delta(before, after, "verifiedByLevel", "NATIONAL")).isEqualTo(1);
        assertThat(delta(before, after, "verifiedByLevel", "ONLINE")).isEqualTo(1);
        assertThat(delta(before, after, "verifiedByLevel", "STATE")).isEqualTo(1);

        JsonNode batchRow = find(after.get("verifiedByBatch"), "batchId", batch.getId());
        assertThat(batchRow).isNotNull();
        assertThat(batchRow.get("verified").asLong()).isEqualTo(3);
        assertThat(batchRow.get("currentYear").asInt()).isEqualTo(2);
        assertThat(find(after.get("verifiedBySection"), "sectionId", section.getId()).get("verified").asLong()).isEqualTo(2);
        assertThat(find(after.get("verifiedBySection"), "sectionId", otherSection.getId()).get("verified").asLong()).isEqualTo(1);
    }

    @Test
    void staffSummaryIsLimitedToTaughtSections() throws Exception {
        seed();
        JsonNode summary = summary(staff);

        assertThat(summary.get("scope").asText()).isEqualTo("ASSIGNED_SECTIONS");
        assertThat(summary.get("verified").asLong()).isEqualTo(2);
        assertThat(summary.get("pendingReview").asLong()).isEqualTo(1);
        assertThat(summary.get("rejected").asLong()).isEqualTo(1);
        assertThat(summary.get("studentsWithVerifiedEntries").asLong()).isEqualTo(1);
        assertThat(summary.get("verifiedByActivityType").get("HACKATHON").asLong()).isEqualTo(1);
        assertThat(summary.get("verifiedByLevel").get("STATE").asLong()).isZero();
        assertThat(summary.get("verifiedBySection")).hasSize(1);
        assertThat(summary.get("verifiedBySection").get(0).get("sectionId").asLong()).isEqualTo(section.getId());

        User newcomer = createUser("ROLE_STAFF", true);
        JsonNode empty = summary(newcomer);
        assertThat(empty.get("verified").asLong()).isZero();
        assertThat(empty.get("verifiedByBatch")).isEmpty();
    }

    private static long delta(JsonNode before, JsonNode after, String map, String key) {
        return after.get(map).get(key).asLong() - before.get(map).get(key).asLong();
    }
}
