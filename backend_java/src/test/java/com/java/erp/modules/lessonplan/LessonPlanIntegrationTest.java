package com.java.erp.modules.lessonplan;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lesson Plans are switched off by default; their own tests run with the flag on. */
@TestPropertySource(properties = "features.lesson-plans.enabled=true")
class LessonPlanIntegrationTest extends ErpModulesTestBase {

    private static Map<String, Object> plan(Long offeringId, String topic) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectOfferingId", offeringId);
        m.put("unitNumber", 1);
        m.put("topic", topic);
        m.put("plannedDate", LocalDate.now().plusDays(3).toString());
        return m;
    }

    private long create(SubjectOffering o, String topic) throws Exception {
        return data(postAs(staff, "/lesson-plans", plan(o.getId(), topic)).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void staffCreatesForOwnOfferingButNotForOthers() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        postAs(staff, "/lesson-plans", plan(own.getId(), "Arrays"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PLANNED"));
        postAs(staff, "/lesson-plans", plan(foreign.getId(), "Arrays")).andExpect(status().isForbidden());
        postAs(hod, "/lesson-plans", plan(own.getId(), "Arrays")).andExpect(status().isForbidden());
    }

    @Test
    void staffEditsPlannedTopicOnly() throws Exception {
        long id = create(theoryOffering(), "Arrays");
        Map<String, Object> edit = Map.of("topic", "Arrays and strings", "plannedDate", LocalDate.now().plusDays(5).toString());
        putAs(staff, "/lesson-plans/" + id, edit).andExpect(status().isOk()).andExpect(jsonPath("$.data.topic").value("Arrays and strings"));
        putAs(otherStaff, "/lesson-plans/" + id, edit).andExpect(status().isForbidden());
        postAs(staff, "/lesson-plans/" + id + "/complete", Map.of()).andExpect(status().isOk());
        putAs(staff, "/lesson-plans/" + id, edit).andExpect(status().isConflict());
    }

    @Test
    void completeCancelAndReopen() throws Exception {
        long id = create(theoryOffering(), "Linked lists");
        postAs(staff, "/lesson-plans/" + id + "/complete", Map.of("completedDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest());
        postAs(staff, "/lesson-plans/" + id + "/complete", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedDate").value(LocalDate.now().toString()));
        postAs(staff, "/lesson-plans/" + id + "/complete", null).andExpect(status().isConflict());
        postAs(staff, "/lesson-plans/" + id + "/reopen", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PLANNED"));
        postAs(staff, "/lesson-plans/" + id + "/cancel", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void progressSummaryCountsPlannedAndCompletedOnly() throws Exception {
        SubjectOffering offering = theoryOffering();
        long a = create(offering, "A");
        create(offering, "B");
        create(offering, "C");
        long d = create(offering, "D");
        postAs(staff, "/lesson-plans/" + a + "/complete", null).andExpect(status().isOk());
        postAs(staff, "/lesson-plans/" + d + "/cancel", null).andExpect(status().isOk());

        getAs(staff, "/lesson-plans/subject-offerings/" + offering.getId() + "/progress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPlanned").value(3))
                .andExpect(jsonPath("$.data.completed").value(1))
                .andExpect(jsonPath("$.data.remaining").value(2))
                .andExpect(jsonPath("$.data.cancelled").value(1))
                .andExpect(jsonPath("$.data.completionPercentage").value(33.33));
        SubjectOffering empty = offering(subject(Subject.SubjectType.THEORY), otherSection, staff);
        getAs(staff, "/lesson-plans/subject-offerings/" + empty.getId() + "/progress")
                .andExpect(jsonPath("$.data.completionPercentage").doesNotExist());
    }

    @Test
    void hodReadsAllWithFilters() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        create(own, "Mine");
        postAs(otherStaff, "/lesson-plans", plan(foreign.getId(), "Theirs")).andExpect(status().isCreated());

        getAs(hod, "/lesson-plans?staffId=" + otherStaff.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].topic").value("Theirs"));
        getAs(hod, "/lesson-plans?sectionId=" + section.getId()).andExpect(jsonPath("$.data.content.length()").value(1));
        getAs(staff, "/lesson-plans").andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void studentReadsPlansOfEnrolledSubjectsOnly() throws Exception {
        SubjectOffering enrolled = theoryOffering();
        SubjectOffering other = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        create(enrolled, "Visible");
        postAs(otherStaff, "/lesson-plans", plan(other.getId(), "Hidden")).andExpect(status().isCreated());
        StudentLogin me = studentLogin(section);
        enroll(enrolled, me.student());

        getAs(me.user(), "/lesson-plans/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].topic").value("Visible"));
        getAs(me.user(), "/lesson-plans/me?subjectOfferingId=" + other.getId()).andExpect(status().isForbidden());
        getAs(me.user(), "/lesson-plans/subject-offerings/" + enrolled.getId() + "/progress").andExpect(status().isOk());
        getAs(me.user(), "/lesson-plans/subject-offerings/" + other.getId() + "/progress").andExpect(status().isForbidden());
        postAs(me.user(), "/lesson-plans", plan(enrolled.getId(), "X")).andExpect(status().isForbidden());
    }
}
