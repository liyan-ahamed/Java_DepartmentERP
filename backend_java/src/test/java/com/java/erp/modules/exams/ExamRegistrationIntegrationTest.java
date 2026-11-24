package com.java.erp.modules.exams;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamRegistrationIntegrationTest extends ErpModulesTestBase {

    private SubjectOffering enrolled;
    private SubjectOffering notEnrolled;
    private StudentLogin me;

    @BeforeEach
    void setUpTerm() {
        enrolled = theoryOffering();
        notEnrolled = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        me = studentLogin(section);
        enroll(enrolled, me.student());
    }

    private Map<String, Object> period(LocalDate start, LocalDate end) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "End Semester " + unique());
        m.put("academicYear", ACADEMIC_YEAR);
        m.put("semester", 3);
        m.put("examType", "END_SEMESTER");
        m.put("registrationStart", start.toString());
        m.put("registrationEnd", end.toString());
        return m;
    }

    private long createPeriod(LocalDate start, LocalDate end) throws Exception {
        return data(postAs(hod, "/exam-periods", period(start, end)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long openPeriod() throws Exception {
        long id = createPeriod(LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));
        postAs(hod, "/exam-periods/" + id + "/open", null).andExpect(status().isOk());
        return id;
    }

    private long submitted(long periodId) throws Exception {
        putAs(me.user(), "/exam-registrations/periods/" + periodId + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isOk());
        return data(postAs(me.user(), "/exam-registrations/periods/" + periodId + "/me/submit", null).andExpect(status().isOk()))
                .get("id").asLong();
    }

    @Test
    void periodLifecycleDraftOpenClosed() throws Exception {
        long id = createPeriod(LocalDate.now(), LocalDate.now().plusDays(3));
        postAs(hod, "/exam-periods/" + id + "/close", null).andExpect(status().isConflict()); // DRAFT -> CLOSED invalid
        postAs(hod, "/exam-periods/" + id + "/open", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OPEN"));
        postAs(hod, "/exam-periods/" + id + "/open", null).andExpect(status().isConflict());
        postAs(hod, "/exam-periods/" + id + "/close", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        putAs(hod, "/exam-periods/" + id, period(LocalDate.now(), LocalDate.now().plusDays(3))).andExpect(status().isConflict());
        postAs(hod, "/exam-periods", period(LocalDate.now(), LocalDate.now().minusDays(1))).andExpect(status().isBadRequest());
    }

    @Test
    void registrationWindowIsEnforced() throws Exception {
        long future = createPeriod(LocalDate.now().plusDays(2), LocalDate.now().plusDays(6));
        postAs(hod, "/exam-periods/" + future + "/open", null).andExpect(status().isOk());
        putAs(me.user(), "/exam-registrations/periods/" + future + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isConflict());

        long draft = createPeriod(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        putAs(me.user(), "/exam-registrations/periods/" + draft + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isNotFound());

        long closed = openPeriod();
        postAs(hod, "/exam-periods/" + closed + "/close", null).andExpect(status().isOk());
        putAs(me.user(), "/exam-registrations/periods/" + closed + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyEnrolledSubjectsAreEligible() throws Exception {
        long id = openPeriod();
        getAs(me.user(), "/exam-registrations/periods/" + id + "/eligible-subjects").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subjectOfferingId").value(enrolled.getId()));
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId(), notEnrolled.getId())))
                .andExpect(status().isBadRequest());
        getAs(me.user(), "/exam-registrations/periods").andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void duplicatesArePrevented() throws Exception {
        long id = openPeriod();
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId(), enrolled.getId())))
                .andExpect(status().isBadRequest());
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isOk());
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isOk()); // same registration updated, not a second one
        getAs(me.user(), "/exam-registrations/me").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subjects.length()").value(1));
    }

    @Test
    void studentSubmitsAndCannotChangeAfterwards() throws Exception {
        long id = openPeriod();
        long reg = submitted(id);
        getAs(me.user(), "/exam-registrations/" + reg).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isConflict());
        postAs(me.user(), "/exam-registrations/periods/" + id + "/me/submit", null).andExpect(status().isConflict());
    }

    @Test
    void hodApprovesSubmittedRegistration() throws Exception {
        long reg = submitted(openPeriod());
        postAs(hod, "/exam-registrations/" + reg + "/approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedById").value(hod.getId()));
        postAs(hod, "/exam-registrations/" + reg + "/approve", null).andExpect(status().isConflict());
        postAs(hod, "/exam-registrations/" + reg + "/reject", Map.of("reviewNote", "Too late")).andExpect(status().isConflict());
    }

    @Test
    void hodRejectsAndStudentCanResubmit() throws Exception {
        long id = openPeriod();
        long reg = submitted(id);
        postAs(hod, "/exam-registrations/" + reg + "/reject", Map.of()).andExpect(status().isBadRequest());
        postAs(hod, "/exam-registrations/" + reg + "/reject", Map.of("reviewNote", "Fee dues pending")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
        postAs(me.user(), "/exam-registrations/periods/" + id + "/me/submit", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void invalidTransitionsAreRejected() throws Exception {
        long id = openPeriod();
        putAs(me.user(), "/exam-registrations/periods/" + id + "/me", Map.of("subjectOfferingIds", List.of(enrolled.getId())))
                .andExpect(status().isOk());
        long reg = data(getAs(me.user(), "/exam-registrations/me")).get(0).get("id").asLong();
        postAs(hod, "/exam-registrations/" + reg + "/approve", null).andExpect(status().isConflict()); // still DRAFT
    }

    @Test
    void accessIsRestrictedByRole() throws Exception {
        long id = openPeriod();
        long reg = submitted(id);
        StudentLogin other = studentLogin(section);

        getAs(other.user(), "/exam-registrations/" + reg).andExpect(status().isForbidden());
        postAs(other.user(), "/exam-registrations/" + reg + "/approve", null).andExpect(status().isForbidden());
        getAs(staff, "/exam-registrations").andExpect(status().isForbidden());
        postAs(staff, "/exam-registrations/" + reg + "/approve", null).andExpect(status().isForbidden());
        postAs(staff, "/exam-periods", period(LocalDate.now(), LocalDate.now())).andExpect(status().isForbidden());
        getAs(staff, "/exam-periods").andExpect(status().isOk()); // read-only for staff
        getAs(me.user(), "/exam-periods").andExpect(status().isForbidden());
        getAs(hod, "/exam-registrations?examPeriodId=" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }
}
