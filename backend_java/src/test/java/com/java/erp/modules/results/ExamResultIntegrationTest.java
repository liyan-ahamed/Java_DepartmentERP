package com.java.erp.modules.results;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.repository.ExamPeriodRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamResultIntegrationTest extends ErpModulesTestBase {

    @Autowired private ExamPeriodRepository periodRepository;

    private SubjectOffering offering;
    private ExamPeriod period;
    private StudentLogin me;
    private Student classmate;

    @BeforeEach
    void setUpResults() {
        offering = theoryOffering();
        me = studentLogin(section);
        classmate = student(section);
        enroll(offering, me.student());
        enroll(offering, classmate);
        ExamPeriod p = new ExamPeriod();
        p.setName("Internal Test " + unique());
        p.setAcademicYear(ACADEMIC_YEAR);
        p.setSemester(3);
        p.setExamType(ExamPeriod.ExamType.INTERNAL);
        p.setRegistrationStart(LocalDate.now().minusDays(20));
        p.setRegistrationEnd(LocalDate.now().minusDays(10));
        p.setStatus(ExamPeriod.Status.CLOSED);
        period = periodRepository.saveAndFlush(p);
    }

    private static Map<String, Object> row(Long studentId, Object marks, String status, String grade) {
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", studentId);
        m.put("marksObtained", marks);
        m.put("maxMarks", 100);
        m.put("resultStatus", status);
        m.put("grade", grade);
        return m;
    }

    private String sheet() {
        return "/results/subject-offerings/" + offering.getId() + "/periods/" + period.getId();
    }

    @Test
    void staffEntersResultsForOwnOffering() throws Exception {
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), 72, "PASS", "a"), row(classmate.getId(), 31, "FAIL", "RA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2));
        getAs(staff, sheet()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entered").value(2))
                .andExpect(jsonPath("$.data.published").value(0));
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), 75, "PASS", "A"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updated").value(1));
    }

    @Test
    void staffCannotEnterResultsForOtherOfferings() throws Exception {
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        enroll(foreign, classmate);
        putAs(staff, "/results/subject-offerings/" + foreign.getId() + "/periods/" + period.getId(),
                Map.of("results", List.of(row(classmate.getId(), 50, "PASS", null)))).andExpect(status().isForbidden());
        putAs(hod, sheet(), Map.of("results", List.of(row(classmate.getId(), 50, "PASS", null)))).andExpect(status().isForbidden());
    }

    @Test
    void unpublishedResultsAreHiddenUntilHodPublishes() throws Exception {
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), 72, "PASS", "A")))).andExpect(status().isOk());
        getAs(me.user(), "/results/me").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        postAs(staff, sheet() + "/publish", null).andExpect(status().isForbidden());
        postAs(hod, sheet() + "/publish", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(1));
        postAs(hod, sheet() + "/publish", null).andExpect(status().isConflict());

        getAs(me.user(), "/results/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].marksObtained").value(72.0))
                .andExpect(jsonPath("$.data[0].grade").value("A"))
                .andExpect(jsonPath("$.data[0].published").value(true));
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), 80, "PASS", "A")))).andExpect(status().isBadRequest());
    }

    @Test
    void absentAndWithheldRules() throws Exception {
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), null, "ABSENT", null), row(classmate.getId(), null, "WITHHELD", null))))
                .andExpect(status().isOk());
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), 40, "ABSENT", null)))).andExpect(status().isBadRequest());
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), null, "ABSENT", "U")))).andExpect(status().isBadRequest());
        putAs(staff, sheet(), Map.of("results", List.of(row(classmate.getId(), 60, "WITHHELD", null)))).andExpect(status().isOk());
        getAs(staff, sheet()).andExpect(jsonPath("$.data.rows[?(@.studentId == " + me.student().getId() + ")].resultStatus").value("ABSENT"));
    }

    @Test
    void validationRejectsTheWholeBatch() throws Exception {
        Student notEnrolled = student(section);
        putAs(staff, sheet(), Map.of("results", List.of(
                row(me.student().getId(), null, "PASS", null),          // marks required
                row(classmate.getId(), 120, "PASS", null),               // above max
                row(notEnrolled.getId(), 50, "PASS", null))))            // not enrolled
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        getAs(staff, sheet()).andExpect(jsonPath("$.data.entered").value(0));

        ExamPeriod otherTerm = new ExamPeriod();
        otherTerm.setName("Other term " + unique());
        otherTerm.setAcademicYear("2025-2026");
        otherTerm.setSemester(2);
        otherTerm.setExamType(ExamPeriod.ExamType.INTERNAL);
        otherTerm.setRegistrationStart(LocalDate.now());
        otherTerm.setRegistrationEnd(LocalDate.now());
        otherTerm.setStatus(ExamPeriod.Status.CLOSED);
        otherTerm = periodRepository.saveAndFlush(otherTerm);
        putAs(staff, "/results/subject-offerings/" + offering.getId() + "/periods/" + otherTerm.getId(),
                Map.of("results", List.of(row(classmate.getId(), 50, "PASS", null)))).andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotUseStaffOrHodEndpoints() throws Exception {
        getAs(me.user(), sheet()).andExpect(status().isForbidden());
        getAs(me.user(), "/results").andExpect(status().isForbidden());
        putAs(me.user(), sheet(), Map.of("results", List.of(row(me.student().getId(), 100, "PASS", "O")))).andExpect(status().isForbidden());
    }

    @Test
    void hodSeesAllResultsAndStaffOnlyTheirs() throws Exception {
        putAs(staff, sheet(), Map.of("results", List.of(row(me.student().getId(), 72, "PASS", null)))).andExpect(status().isOk());
        getAs(hod, "/results?examPeriodId=" + period.getId()).andExpect(jsonPath("$.data.content.length()").value(1));
        getAs(otherStaff, "/results?examPeriodId=" + period.getId()).andExpect(jsonPath("$.data.content.length()").value(0));
        getAs(otherStaff, sheet()).andExpect(status().isForbidden());
    }
}
