package com.java.erp.modules.portfolio;

import com.java.erp.support.PortfolioTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioValidationIntegrationTest extends PortfolioTestBase {

    @Test
    void titleAndActivityTypeAreRequired() throws Exception {
        Map<String, Object> body = completeEntry();
        body.remove("title");
        body.remove("activityType");
        create(student.user(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(startsWith("title:"))))
                .andExpect(jsonPath("$.errors", hasItem(startsWith("activityType:"))));

        Map<String, Object> blank = completeEntry();
        blank.put("title", "   ");
        create(student.user(), blank).andExpect(status().isBadRequest());
    }

    @Test
    void invalidEnumValuesAreRejected() throws Exception {
        Map<String, Object> badType = completeEntry();
        badType.put("activityType", "PARTY");
        create(student.user(), badType).andExpect(status().isBadRequest());

        Map<String, Object> badLevel = completeEntry();
        badLevel.put("level", "GALACTIC");
        create(student.user(), badLevel).andExpect(status().isBadRequest());

        mvc.perform(get("/portfolio/me").param("status", "APPROVED").header("Authorization", token(student.user())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endDateMustNotBeBeforeStartDate() throws Exception {
        Map<String, Object> body = completeEntry();
        body.put("startDate", "2026-03-10");
        body.put("endDate", "2026-03-09");
        create(student.user(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(startsWith("endDate:"))));

        body.put("endDate", "2026-03-10"); // same day is fine
        create(student.user(), body).andExpect(status().isCreated());
    }

    @Test
    void evidenceUrlMustBeAWebLink() throws Exception {
        for (String bad : new String[]{"not a url", "www.example.com/cert", "ftp://example.com/cert.pdf",
                "javascript:alert(1)", "https://"}) {
            Map<String, Object> body = completeEntry();
            body.put("evidenceUrl", bad);
            create(student.user(), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasItem(startsWith("evidenceUrl:"))));
        }
        Map<String, Object> ok = completeEntry();
        ok.put("evidenceUrl", "http://github.com/student/project");
        create(student.user(), ok).andExpect(status().isCreated());
    }

    @Test
    void overlongFieldsAreRejected() throws Exception {
        Map<String, Object> body = completeEntry();
        body.put("title", "x".repeat(201));
        create(student.user(), body).andExpect(status().isBadRequest());

        Map<String, Object> longDescription = completeEntry();
        longDescription.put("description", "x".repeat(2001));
        create(student.user(), longDescription).andExpect(status().isBadRequest());
    }

    @Test
    void incompleteDraftCannotBeSubmitted() throws Exception {
        Long id = draft(student, Map.of("activityType", "INTERNSHIP", "title", "Backend intern"));

        submit(student.user(), id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(startsWith("organization:"))))
                .andExpect(jsonPath("$.errors", hasItem(startsWith("description:"))))
                .andExpect(jsonPath("$.errors", hasItem(startsWith("level:"))))
                .andExpect(jsonPath("$.errors", hasItem(startsWith("startDate:"))));

        mvc.perform(get("/portfolio/entries/" + id).header("Authorization", token(student.user())))
                .andExpect(jsonPath("$.data.status", is("DRAFT")));
    }

    @Test
    void submissionNeedsAMeaningfulDescriptionAndAPastStartDate() throws Exception {
        Map<String, Object> shortDescription = completeEntry();
        shortDescription.put("description", "Won it");
        submit(student.user(), draft(student, shortDescription))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(startsWith("description:"))));

        Map<String, Object> future = completeEntry();
        future.put("startDate", LocalDate.now().plusDays(5).toString());
        future.remove("endDate");
        submit(student.user(), draft(student, future))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(startsWith("startDate:"))));
    }

    @Test
    void wrongStateTransitionsAreConflicts() throws Exception {
        Long draftId = draft(student);
        verify(staff, draftId, null).andExpect(status().isNotFound()); // drafts are private
        reject(staff, draftId, "Not allowed on a draft").andExpect(status().isNotFound());

        Long id = submitted(student);
        verify(staff, id, null).andExpect(status().isOk());
        verify(staff, id, null).andExpect(status().isConflict());
        reject(staff, id, "Too late to reject this").andExpect(status().isConflict());
    }

    @Test
    void malformedReviewBodiesAreBadRequests() throws Exception {
        Long id = submitted(student);
        mvc.perform(post("/portfolio/entries/" + id + "/reject").header("Authorization", token(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        Map<String, Object> tooLong = new HashMap<>();
        tooLong.put("reviewNote", "x".repeat(1001));
        mvc.perform(post("/portfolio/entries/" + id + "/verify").header("Authorization", token(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(json(tooLong)))
                .andExpect(status().isBadRequest());
    }
}
