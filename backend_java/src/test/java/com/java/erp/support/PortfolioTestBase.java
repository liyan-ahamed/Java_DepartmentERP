package com.java.erp.support;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.portfolio.repository.PortfolioEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portfolio fixtures: {@link #staff} teaches {@link #section} and {@link #otherStaff} teaches
 * {@link #otherSection} (active subject offerings); one student login in each section.
 */
public abstract class PortfolioTestBase extends AcademicTestBase {

    @Autowired protected PortfolioEntryRepository portfolioRepository;

    protected StudentLogin student;
    protected StudentLogin otherStudent;

    @BeforeEach
    void setUpPortfolioFixture() {
        theoryOffering();
        offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        student = studentLogin(section);
        otherStudent = studentLogin(otherSection);
    }

    /** A request with every field filled in, valid for submission. */
    protected static Map<String, Object> completeEntry(String type, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("activityType", type);
        body.put("title", title);
        body.put("organization", "Ministry of Education");
        body.put("description", "Built a flood alert app with a five-member team in 36 hours.");
        body.put("level", "NATIONAL");
        body.put("roleOrPosition", "Team lead");
        body.put("achievementResult", "Winner");
        body.put("startDate", LocalDate.now().minusMonths(2).toString());
        body.put("endDate", LocalDate.now().minusMonths(2).plusDays(1).toString());
        body.put("evidenceUrl", "https://www.sih.gov.in/results");
        return body;
    }

    protected static Map<String, Object> completeEntry() {
        return completeEntry("HACKATHON", "Smart India Hackathon 2026");
    }

    protected ResultActions create(User user, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/portfolio/entries").header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions update(User user, Long id, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/portfolio/entries/" + id).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions submit(User user, Long id) throws Exception {
        return mvc.perform(post("/portfolio/entries/" + id + "/submit").header("Authorization", token(user)));
    }

    protected ResultActions verify(User user, Long id, String note) throws Exception {
        return mvc.perform(post("/portfolio/entries/" + id + "/verify").header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(note == null ? Map.of() : Map.of("reviewNote", note))));
    }

    protected ResultActions reject(User user, Long id, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reviewNote", note);
        return mvc.perform(post("/portfolio/entries/" + id + "/reject").header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected Long draft(StudentLogin owner, Map<String, Object> body) throws Exception {
        return data(create(owner.user(), body).andExpect(status().isCreated())).get("id").asLong();
    }

    protected Long draft(StudentLogin owner) throws Exception {
        return draft(owner, completeEntry());
    }

    protected Long submitted(StudentLogin owner, Map<String, Object> body) throws Exception {
        Long id = draft(owner, body);
        submit(owner.user(), id).andExpect(status().isOk());
        return id;
    }

    protected Long submitted(StudentLogin owner) throws Exception {
        return submitted(owner, completeEntry());
    }
}
