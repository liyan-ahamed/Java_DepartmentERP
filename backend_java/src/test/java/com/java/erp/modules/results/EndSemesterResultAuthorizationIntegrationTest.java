package com.java.erp.modules.results;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.repository.ExamPeriodRepository;
import com.java.erp.modules.results.repository.ExamResultRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-semester (and supplementary) results are read-only for STAFF; the COE enters them, the HOD publishes. */
class EndSemesterResultAuthorizationIntegrationTest extends ErpModulesTestBase {

    @Autowired private ExamPeriodRepository periodRepository;
    @Autowired private ExamResultRepository resultRepository;

    private SubjectOffering offering;
    private Student first;
    private Student second;
    private User coe;

    @BeforeEach
    void setUpEndSemester() {
        offering = theoryOffering();
        first = student(section);
        second = student(section);
        enroll(offering, first);
        enroll(offering, second);
        coe = createUser("ROLE_COE", true);
    }

    private ExamPeriod period(ExamPeriod.ExamType type) {
        ExamPeriod p = new ExamPeriod();
        p.setName(type + " " + unique());
        p.setAcademicYear(ACADEMIC_YEAR);
        p.setSemester(3);
        p.setExamType(type);
        p.setRegistrationStart(LocalDate.now().minusDays(20));
        p.setRegistrationEnd(LocalDate.now().minusDays(10));
        p.setStatus(ExamPeriod.Status.CLOSED);
        return periodRepository.saveAndFlush(p);
    }

    private String sheet(SubjectOffering o, ExamPeriod p) {
        return "/results/subject-offerings/" + o.getId() + "/periods/" + p.getId();
    }

    private Map<String, Object> body() {
        return Map.of("results", List.of(row(first.getId(), 72), row(second.getId(), 31)));
    }

    private static Map<String, Object> row(Long studentId, int marks) {
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", studentId);
        m.put("marksObtained", marks);
        m.put("maxMarks", 100);
        m.put("resultStatus", marks >= 40 ? "PASS" : "FAIL");
        return m;
    }

    private String byExam(String exam) {
        return "/results/by-exam?batchId=" + batch.getId() + "&semester=3&exam=" + exam;
    }

    @Test
    void staffCannotEnterEndSemesterOrSupplementaryResultsEvenForOwnOffering() throws Exception {
        for (ExamPeriod.ExamType type : List.of(ExamPeriod.ExamType.END_SEMESTER, ExamPeriod.ExamType.SUPPLEMENTARY)) {
            ExamPeriod p = period(type);
            putAs(staff, sheet(offering, p), body()).andExpect(status().isForbidden());
            putAs(hod, sheet(offering, p), body()).andExpect(status().isForbidden());
            assertThat(resultRepository.findBySubjectOfferingIdAndExamPeriodId(offering.getId(), p.getId())).isEmpty();
            // Reading stays allowed for the assigned staff member.
            getAs(staff, sheet(offering, p)).andExpect(status().isOk());
        }
    }

    @Test
    void coeEntersEndSemesterResultsAndOnlyHodPublishes() throws Exception {
        ExamPeriod p = period(ExamPeriod.ExamType.END_SEMESTER);
        getAs(coe, "/exam-periods/" + p.getId()).andExpect(status().isOk());

        putAs(coe, sheet(offering, p), body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(2));
        getAs(coe, sheet(offering, p)).andExpect(status().isOk()).andExpect(jsonPath("$.data.entered").value(2));

        postAs(coe, sheet(offering, p) + "/publish", null).andExpect(status().isForbidden());
        postAs(staff, sheet(offering, p) + "/publish", null).andExpect(status().isForbidden());
        postAs(hod, sheet(offering, p) + "/publish", null).andExpect(status().isOk());
        // Published results are immutable, also for the COE.
        putAs(coe, sheet(offering, p), body()).andExpect(status().isBadRequest());
    }

    @Test
    void internalResultsStayWithTheAssignedStaffMember() throws Exception {
        ExamPeriod p = period(ExamPeriod.ExamType.INTERNAL);
        putAs(coe, sheet(offering, p), body()).andExpect(status().isForbidden());
        putAs(staff, sheet(offering, p), body()).andExpect(status().isOk());
    }

    @Test
    void readOnlyViewShowsStaffOnlyPublishedEndSemesterResultsOfTheirClasses() throws Exception {
        ExamPeriod p = period(ExamPeriod.ExamType.END_SEMESTER);
        putAs(coe, sheet(offering, p), body()).andExpect(status().isOk());
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);

        getAs(staff, byExam("END_SEMESTER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(0));
        getAs(hod, byExam("END_SEMESTER")).andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[0].published").value(false));

        postAs(hod, sheet(offering, p) + "/publish", null).andExpect(status().isOk());
        getAs(staff, byExam("END_SEMESTER")).andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[0].source").value("EXAM_RESULT"))
                .andExpect(jsonPath("$.data.content[0].published").value(true));
        getAs(otherStaff, byExam("END_SEMESTER")).andExpect(jsonPath("$.data.total_elements").value(0));
        getAs(otherStaff, byExam("END_SEMESTER") + "&subjectOfferingId=" + offering.getId()).andExpect(status().isForbidden());
        getAs(staff, byExam("END_SEMESTER") + "&subjectOfferingId=" + foreign.getId()).andExpect(status().isForbidden());
        // Another semester of the same cohort shows nothing (no cross-semester leakage).
        getAs(hod, "/results/by-exam?batchId=" + batch.getId() + "&semester=4&exam=END_SEMESTER")
                .andExpect(jsonPath("$.data.total_elements").value(0));
        getAs(coe, byExam("END_SEMESTER")).andExpect(status().isForbidden());
    }
}
