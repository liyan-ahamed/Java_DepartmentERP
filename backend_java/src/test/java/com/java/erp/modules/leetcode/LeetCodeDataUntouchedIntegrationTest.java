package com.java.erp.modules.leetcode;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard: the new ERP modules only reference students by id. Running enrollment, attendance,
 * fees, exam registration, results and feedback for LeetCode-linked students must leave every
 * LeetCode field (and the student's identity) exactly as it was.
 */
class LeetCodeDataUntouchedIntegrationTest extends ErpModulesTestBase {

    @Autowired private EntityManager entityManager;

    private record Snapshot(String registerNumber, String name, Long sectionId, boolean active, String url, String username,
                            Integer total, Integer easy, Integer medium, Integer hard, LocalDateTime synced) {
        static Snapshot of(Student s) {
            return new Snapshot(s.getRegisterNumber(), s.getName(), s.getSection().getId(), s.isActive(), s.getLeetcodeUrl(),
                    s.getLeetcodeUsername(), s.getLeetcodeTotalSolved(), s.getLeetcodeEasySolved(), s.getLeetcodeMediumSolved(),
                    s.getLeetcodeHardSolved(), s.getLeetcodeLastSyncedAt());
        }
    }

    @Test
    void erpModulesNeverWriteLeetCodeFields() throws Exception {
        StudentLogin me = studentLogin(section);
        Student linked = me.student();
        linked.setLeetcodeUrl("https://leetcode.com/u/someone/");
        linked.setLeetcodeUsername("someone");
        linked.setLeetcodeTotalSolved(321);
        linked.setLeetcodeEasySolved(150);
        linked.setLeetcodeMediumSolved(140);
        linked.setLeetcodeHardSolved(31);
        linked.setLeetcodeLastSyncedAt(LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS));
        linked = studentRepository.saveAndFlush(linked);
        Snapshot before = Snapshot.of(linked);
        Long id = linked.getId();

        SubjectOffering offering = theoryOffering();
        postAs(hod, "/course-enrollments/subject-offerings/" + offering.getId() + "/enroll-section", null).andExpect(status().isOk());

        long session = data(postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", offering.getId(),
                "attendanceDate", LocalDate.now().toString())).andExpect(status().isCreated())).get("id").asLong();
        putAs(staff, "/attendance/sessions/" + session + "/records",
                Map.of("records", List.of(Map.of("studentId", id, "status", "ABSENT")))).andExpect(status().isOk());
        postAs(staff, "/attendance/sessions/" + session + "/finalize", null).andExpect(status().isOk());

        long fee = data(postAs(hod, "/fees/structures", Map.of("name", "Fee " + unique(), "feeType", "LAB",
                "academicYear", ACADEMIC_YEAR, "amount", "10.00")).andExpect(status().isCreated())).get("id").asLong();
        postAs(hod, "/fees/structures/" + fee + "/assign", Map.of("studentIds", List.of(id))).andExpect(status().isOk());

        long form = data(postAs(hod, "/feedback/forms", Map.of("title", "F", "targetType", "SECTION", "targetSectionId", section.getId(),
                "questions", List.of(Map.of("questionText", "Q", "questionType", "RATING")))).andExpect(status().isCreated())).get("id").asLong();
        var opened = data(postAs(hod, "/feedback/forms/" + form + "/open", null).andExpect(status().isOk()));
        postAs(me.user(), "/feedback/forms/" + form + "/submit", Map.of("answers", List.of(
                Map.of("questionId", opened.get("questions").get(0).get("id").asLong(), "rating", 5)))).andExpect(status().isOk());
        getAs(me.user(), "/attendance/me").andExpect(status().isOk());
        getAs(me.user(), "/fees/me").andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        Snapshot after = Snapshot.of(studentRepository.findById(id).orElseThrow());
        assertThat(after).isEqualTo(before);
    }
}
