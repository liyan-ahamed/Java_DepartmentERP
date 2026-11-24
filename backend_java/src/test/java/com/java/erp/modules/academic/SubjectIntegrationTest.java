package com.java.erp.modules.academic;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.support.AcademicTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubjectIntegrationTest extends AcademicTestBase {

    private Map<String, Object> body(String code, String name, String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("subjectType", type);
        return body;
    }

    @Test
    void hodCreatesValidSubject() throws Exception {
        String code = "cs" + unique();
        mvc.perform(post("/subjects").header("Authorization", token(hod))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body(code, "Computer Networks", "THEORY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is(code.toUpperCase())))
                .andExpect(jsonPath("$.data.subjectType", is("THEORY")))
                .andExpect(jsonPath("$.data.active", is(true)));
    }

    @Test
    void duplicateCodeIsRejectedIgnoringCase() throws Exception {
        Subject existing = subject(Subject.SubjectType.THEORY);
        mvc.perform(post("/subjects").header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(existing.getCode().toLowerCase(), "Another", "THEORY_CUM_PRACTICAL"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidOrMissingFieldsAreBadRequest() throws Exception {
        String hodToken = token(hod);
        Map<String, Object> negativeCredits = body("CS" + unique(), "Networks", "THEORY");
        negativeCredits.put("credits", -1);
        // A missing subjectType is no longer an error: the subject is stored unclassified (V33).
        for (Map<String, Object> bad : new Map[]{
                body("CS" + unique(), "Networks", "PRACTICAL"),
                negativeCredits,
                body(" ", "Networks", "THEORY"),
                body("CS" + unique(), "", "THEORY")}) {
            mvc.perform(post("/subjects").header("Authorization", hodToken)
                            .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void staffCanReadButNotManageSubjects() throws Exception {
        Subject existing = subject(Subject.SubjectType.THEORY);
        mvc.perform(post("/subjects").header("Authorization", token(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body("CS" + unique(), "Networks", "THEORY"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/subjects/" + existing.getId()).header("Authorization", token(staff)))
                .andExpect(status().isOk());
        mvc.perform(get("/subjects").header("Authorization", token(studentLogin(section).user())))
                .andExpect(status().isForbidden());
    }

    @Test
    void subjectTypeIsFixedOnceOffered() throws Exception {
        Subject offered = subject(Subject.SubjectType.THEORY);
        offering(offered, section, staff);
        mvc.perform(put("/subjects/" + offered.getId()).header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(offered.getCode(), "Renamed", "THEORY_CUM_PRACTICAL"))))
                .andExpect(status().isConflict());
        mvc.perform(put("/subjects/" + offered.getId()).header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(offered.getCode(), "Renamed", "THEORY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Renamed")));
        mvc.perform(put("/subjects/999999999").header("Authorization", token(hod)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body("X1", "Renamed", "THEORY"))))
                .andExpect(status().isNotFound());
    }
}
