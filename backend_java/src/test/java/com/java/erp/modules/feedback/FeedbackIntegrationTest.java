package com.java.erp.modules.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.audit.repository.AuditLogRepository;
import com.java.erp.modules.feedback.repository.FeedbackResponseRepository;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackIntegrationTest extends ErpModulesTestBase {

    @Autowired private FeedbackResponseRepository responseRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private Map<String, Object> form(String targetType, String idField, Long targetId, boolean anonymous) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", "Course feedback " + unique());
        m.put("targetType", targetType);
        if (idField != null) m.put(idField, targetId);
        m.put("anonymous", anonymous);
        m.put("questions", List.of(
                Map.of("questionText", "Clarity of teaching", "questionType", "RATING"),
                Map.of("questionText", "Pace", "questionType", "RATING", "required", false),
                Map.of("questionText", "Suggestions", "questionType", "TEXT", "required", false)));
        return m;
    }

    private JsonNode openForm(Map<String, Object> body) throws Exception {
        long id = data(postAs(hod, "/feedback/forms", body).andExpect(status().isCreated())).get("id").asLong();
        return data(postAs(hod, "/feedback/forms/" + id + "/open", null).andExpect(status().isOk()));
    }

    private static List<Map<String, Object>> answers(JsonNode form, int rating, String text) {
        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode q = form.get("questions");
        list.add(Map.of("questionId", q.get(0).get("id").asLong(), "rating", rating));
        if (text != null) list.add(Map.of("questionId", q.get(2).get("id").asLong(), "textAnswer", text));
        return list;
    }

    private void submit(StudentLogin s, JsonNode form, int rating, String text) throws Exception {
        postAs(s.user(), "/feedback/forms/" + form.get("id").asLong() + "/submit", Map.of("answers", answers(form, rating, text)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.submitted").value(true));
    }

    @Test
    void hodCreatesFormWithNumberedQuestions() throws Exception {
        postAs(hod, "/feedback/forms", form("DEPARTMENT", null, null, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[2].position").value(3))
                .andExpect(jsonPath("$.data.questions[1].required").value(false));
        postAs(hod, "/feedback/forms", form("SECTION", null, null, true)).andExpect(status().isBadRequest());           // missing id
        postAs(hod, "/feedback/forms", form("DEPARTMENT", "targetBatchId", batch.getId(), true)).andExpect(status().isBadRequest());
        postAs(staff, "/feedback/forms", form("DEPARTMENT", null, null, true)).andExpect(status().isForbidden());
    }

    @Test
    void draftFormCanBeEditedOnlyBeforeOpening() throws Exception {
        long id = data(postAs(hod, "/feedback/forms", form("DEPARTMENT", null, null, true))).get("id").asLong();
        Map<String, Object> edit = form("BATCH", "targetBatchId", batch.getId(), false);
        edit.put("questions", List.of(Map.of("questionText", "Only one", "questionType", "TEXT")));
        putAs(hod, "/feedback/forms/" + id, edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(1))
                .andExpect(jsonPath("$.data.targetType").value("BATCH"));
        postAs(hod, "/feedback/forms/" + id + "/open", null).andExpect(status().isOk());
        putAs(hod, "/feedback/forms/" + id, edit).andExpect(status().isConflict());
    }

    @Test
    void targetingDecidesWhoSeesTheForm() throws Exception {
        SubjectOffering offering = theoryOffering();
        StudentLogin inSection = studentLogin(section);
        StudentLogin otherSec = studentLogin(otherSection);
        enroll(offering, inSection.student());

        JsonNode bySection = openForm(form("SECTION", "targetSectionId", section.getId(), true));
        JsonNode byOffering = openForm(form("SUBJECT_OFFERING", "targetSubjectOfferingId", offering.getId(), true));
        JsonNode byBatch = openForm(form("BATCH", "targetBatchId", batch.getId(), true));

        Set<Long> seenIn = ids(data(getAs(inSection.user(), "/feedback/forms/me")));
        Set<Long> seenOther = ids(data(getAs(otherSec.user(), "/feedback/forms/me")));
        assertThat(seenIn).contains(bySection.get("id").asLong(), byOffering.get("id").asLong(), byBatch.get("id").asLong());
        assertThat(seenOther).contains(byBatch.get("id").asLong())
                .doesNotContain(bySection.get("id").asLong(), byOffering.get("id").asLong());

        postAs(otherSec.user(), "/feedback/forms/" + bySection.get("id").asLong() + "/submit",
                Map.of("answers", answers(bySection, 3, null))).andExpect(status().isForbidden());
        getAs(otherSec.user(), "/feedback/forms/" + byOffering.get("id").asLong()).andExpect(status().isNotFound());
    }

    @Test
    void studentSubmitsOnceWithValidatedAnswers() throws Exception {
        StudentLogin me = studentLogin(section);
        JsonNode f = openForm(form("SECTION", "targetSectionId", section.getId(), true));
        long id = f.get("id").asLong();
        long rating = f.get("questions").get(0).get("id").asLong();
        long text = f.get("questions").get(2).get("id").asLong();

        postAs(me.user(), "/feedback/forms/" + id + "/submit", Map.of("answers", List.of(Map.of("questionId", rating, "rating", 6))))
                .andExpect(status().isBadRequest());
        postAs(me.user(), "/feedback/forms/" + id + "/submit", Map.of("answers", List.of(Map.of("questionId", text, "textAnswer", "Hi"))))
                .andExpect(status().isBadRequest()); // required rating question missing
        postAs(me.user(), "/feedback/forms/" + id + "/submit", Map.of("answers", List.of(Map.of("questionId", rating, "textAnswer", "x"))))
                .andExpect(status().isBadRequest());

        submit(me, f, 5, "Great tutorials");
        postAs(me.user(), "/feedback/forms/" + id + "/submit", Map.of("answers", answers(f, 4, null))).andExpect(status().isConflict());
        getAs(me.user(), "/feedback/forms/me").andExpect(jsonPath("$.data[?(@.id == " + id + ")].submitted").value(true));
    }

    @Test
    void anonymousResponsesNeverExposeIdentity() throws Exception {
        StudentLogin a = studentLogin(section);
        StudentLogin b = studentLogin(section);
        JsonNode f = openForm(form("SECTION", "targetSectionId", section.getId(), true));
        long id = f.get("id").asLong();
        submit(a, f, 5, "Alpha comment");
        submit(b, f, 3, "Beta comment");

        String responses = getAs(hod, "/feedback/forms/" + id + "/responses").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String analytics = getAs(hod, "/feedback/forms/" + id + "/analytics").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (StudentLogin s : List.of(a, b)) {
            for (String body : List.of(responses, analytics)) {
                assertThat(body).doesNotContain(s.student().getRegisterNumber()).doesNotContain(s.student().getName())
                        .doesNotContain(s.user().getEmail());
            }
        }
        JsonNode items = objectMapper.readTree(responses).get("data").get("responses");
        for (JsonNode item : items) {
            assertThat(item.get("studentId").isNull()).isTrue();
            assertThat(item.get("submittedAt").isNull()).isTrue();
        }
        // Stored anonymous responses carry no student and no time; submissions are not audited.
        responseRepository.findByFormId(id).forEach(r -> {
            assertThat(r.getStudent()).isNull();
            assertThat(r.getSubmittedAt()).isNull();
        });
        assertThat(auditLogRepository.findAll()).noneMatch(l -> "FEEDBACK".equals(l.getModule()) && "SUBMIT".equals(l.getAction()));
    }

    @Test
    void identifiedFormShowsStudentToHod() throws Exception {
        StudentLogin me = studentLogin(section);
        JsonNode f = openForm(form("SECTION", "targetSectionId", section.getId(), false));
        submit(me, f, 4, null);
        getAs(hod, "/feedback/forms/" + f.get("id").asLong() + "/responses").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responses[0].studentId").value(me.student().getId()))
                .andExpect(jsonPath("$.data.responses[0].registerNumber").value(me.student().getRegisterNumber()));
    }

    @Test
    void closedFormBlocksSubmission() throws Exception {
        StudentLogin me = studentLogin(section);
        JsonNode f = openForm(form("SECTION", "targetSectionId", section.getId(), true));
        long id = f.get("id").asLong();
        postAs(hod, "/feedback/forms/" + id + "/close", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        postAs(hod, "/feedback/forms/" + id + "/close", null).andExpect(status().isConflict());
        postAs(me.user(), "/feedback/forms/" + id + "/submit", Map.of("answers", answers(f, 4, null))).andExpect(status().isConflict());
        assertThat(ids(data(getAs(me.user(), "/feedback/forms/me")))).doesNotContain(id);
    }

    @Test
    void hodAnalyticsAggregatesRatingsAndTexts() throws Exception {
        StudentLogin a = studentLogin(section);
        StudentLogin b = studentLogin(section);
        StudentLogin c = studentLogin(section);
        JsonNode f = openForm(form("SECTION", "targetSectionId", section.getId(), true));
        submit(a, f, 5, "Zebra idea");
        submit(b, f, 4, "Apple idea");
        submit(c, f, 4, null);

        JsonNode analytics = data(getAs(hod, "/feedback/forms/" + f.get("id").asLong() + "/analytics").andExpect(status().isOk()));
        assertThat(analytics.get("submissions").asLong()).isEqualTo(3);
        assertThat(analytics.get("eligibleStudents").asLong()).isEqualTo(3);
        JsonNode q1 = analytics.get("questions").get(0);
        assertThat(q1.get("answers").asLong()).isEqualTo(3);
        assertThat(q1.get("averageRating").decimalValue()).isEqualByComparingTo("4.33");
        assertThat(q1.get("ratingDistribution").get("4").asLong()).isEqualTo(2);
        JsonNode texts = analytics.get("questions").get(2).get("textAnswers");
        assertThat(texts.get(0).asText()).isEqualTo("Apple idea"); // alphabetical, not submission order
        assertThat(texts.get(1).asText()).isEqualTo("Zebra idea");
        getAs(staff, "/feedback/forms/" + f.get("id").asLong() + "/analytics").andExpect(status().isForbidden());
        getAs(a.user(), "/feedback/forms/" + f.get("id").asLong() + "/analytics").andExpect(status().isForbidden());
    }

    private static Set<Long> ids(JsonNode list) {
        Set<Long> ids = new HashSet<>();
        list.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }
}
