package com.java.erp.modules.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository;
import com.java.erp.modules.notification.repository.NotificationRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceIntegrationTest extends ErpModulesTestBase {

    @Autowired private StudentAttendanceRepository attendanceRepository;
    @Autowired private NotificationRepository notificationRepository;

    private SubjectOffering offering;
    private List<Student> classList;

    @BeforeEach
    void setUpClass() {
        offering = theoryOffering();
        classList = students(section, 3);
        enrollAll(offering, classList);
    }

    private long createSession(LocalDate date) throws Exception {
        return data(postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", offering.getId(),
                "attendanceDate", date.toString(), "topic", "Intro")).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> mark(Long studentId, String status) {
        return Map.of("studentId", studentId, "status", status);
    }

    private void markAll(long sessionId, String... statuses) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) rows.add(mark(classList.get(i).getId(), statuses[i]));
        putAs(staff, "/attendance/sessions/" + sessionId + "/records", Map.of("records", rows)).andExpect(status().isOk());
    }

    private void finalized(LocalDate date, String... statuses) throws Exception {
        long id = createSession(date);
        markAll(id, statuses);
        postAs(staff, "/attendance/sessions/" + id + "/finalize", null).andExpect(status().isOk());
    }

    @Test
    void staffCreatesSessionForOwnOfferingOnly() throws Exception {
        long id = createSession(LocalDate.now().minusDays(1));
        getAs(staff, "/attendance/sessions/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));

        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", foreign.getId(), "attendanceDate", LocalDate.now().toString()))
                .andExpect(status().isForbidden());
        postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", offering.getId(), "attendanceDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest());
        putAs(otherStaff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(mark(classList.get(0).getId(), "PRESENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullRosterShowsNotMarkedStudents() throws Exception {
        long id = createSession(LocalDate.now());
        putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(mark(classList.get(0).getId(), "PRESENT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notMarked").value(2));
        JsonNode roster = data(getAs(staff, "/attendance/sessions/" + id + "/records").andExpect(status().isOk()));
        assertThat(roster.get("rosterSize").asInt()).isEqualTo(3);
        assertThat(roster.get("marked").asInt()).isEqualTo(1);
        long notMarked = 0;
        for (JsonNode r : roster.get("records")) if (r.get("status").asText().equals("NOT_MARKED")) notMarked++;
        assertThat(notMarked).isEqualTo(2);
    }

    @Test
    void bulkSaveCreatesThenUpdates() throws Exception {
        long id = createSession(LocalDate.now());
        markAll(id, "PRESENT", "ABSENT", "ON_DUTY");
        JsonNode second = data(putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(
                mark(classList.get(0).getId(), "PRESENT"), mark(classList.get(1).getId(), "EXCUSED")))).andExpect(status().isOk()));
        assertThat(second.get("created").asInt()).isZero();
        assertThat(second.get("updated").asInt()).isEqualTo(1);
        assertThat(second.get("unchanged").asInt()).isEqualTo(1);
        assertThat(attendanceRepository.findBySessionId(id)).hasSize(3);
    }

    @Test
    void invalidStudentRollsBackTheWholeRequest() throws Exception {
        long id = createSession(LocalDate.now());
        Student notEnrolled = student(section);
        putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(
                mark(classList.get(0).getId(), "PRESENT"), mark(notEnrolled.getId(), "PRESENT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1));
        assertThat(attendanceRepository.findBySessionId(id)).isEmpty();
    }

    @Test
    void duplicateStudentIdsAreRejected() throws Exception {
        long id = createSession(LocalDate.now());
        putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(
                mark(classList.get(0).getId(), "PRESENT"), mark(classList.get(0).getId(), "ABSENT"))))
                .andExpect(status().isBadRequest());
        assertThat(attendanceRepository.findBySessionId(id)).isEmpty();
    }

    @Test
    void finalizationIsBlockedWhileStudentsAreUnmarkedAndNobodyIsMarkedAbsentSilently() throws Exception {
        long id = createSession(LocalDate.now());
        putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(mark(classList.get(0).getId(), "PRESENT"))))
                .andExpect(status().isOk());
        postAs(staff, "/attendance/sessions/" + id + "/finalize", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        assertThat(attendanceRepository.findBySessionId(id)).hasSize(1);
        getAs(staff, "/attendance/sessions/" + id).andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void finalizedSessionIsImmutable() throws Exception {
        long id = createSession(LocalDate.now());
        markAll(id, "PRESENT", "PRESENT", "ABSENT");
        postAs(staff, "/attendance/sessions/" + id + "/finalize", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FINALIZED"));

        postAs(staff, "/attendance/sessions/" + id + "/finalize", null).andExpect(status().isConflict());
        putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(mark(classList.get(2).getId(), "PRESENT"))))
                .andExpect(status().isConflict());
        putAs(staff, "/attendance/sessions/" + id, Map.of("attendanceDate", LocalDate.now().toString())).andExpect(status().isConflict());
        deleteAs(staff, "/attendance/sessions/" + id).andExpect(status().isConflict());
    }

    @Test
    void draftSessionCanBeEditedAndDeleted() throws Exception {
        long id = createSession(LocalDate.now().minusDays(2));
        putAs(staff, "/attendance/sessions/" + id, Map.of("attendanceDate", LocalDate.now().minusDays(1).toString(), "topic", "Changed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.topic").value("Changed"));
        markAll(id, "PRESENT", "PRESENT", "PRESENT");
        deleteAs(staff, "/attendance/sessions/" + id).andExpect(status().isOk());
        getAs(staff, "/attendance/sessions/" + id).andExpect(status().isNotFound());
    }

    @Test
    void percentagesUseFinalizedSessionsOnlyWithPolicy() throws Exception {
        LocalDate d = LocalDate.now().minusDays(10);
        finalized(d, "PRESENT", "ABSENT", "EXCUSED");
        finalized(d.plusDays(1), "ON_DUTY", "ABSENT", "EXCUSED");
        finalized(d.plusDays(2), "PRESENT", "PRESENT", "EXCUSED");
        finalized(d.plusDays(3), "ABSENT", "ABSENT", "EXCUSED");
        long draft = createSession(d.plusDays(4));
        markAll(draft, "ABSENT", "ABSENT", "ABSENT"); // DRAFT: must not count

        JsonNode summary = data(getAs(staff, "/attendance/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isOk()));
        assertThat(summary.get("finalizedSessions").asLong()).isEqualTo(4);
        Map<Long, JsonNode> rows = new HashMap<>();
        summary.get("students").forEach(r -> rows.put(r.get("studentId").asLong(), r));

        JsonNode first = rows.get(classList.get(0).getId());   // P, OD, P, A -> 3/4
        assertThat(first.get("attended").asLong()).isEqualTo(3);
        assertThat(first.get("onDuty").asLong()).isEqualTo(1);
        assertThat(first.get("attendancePercentage").decimalValue()).isEqualByComparingTo("75.00");
        assertThat(first.get("belowThreshold").asBoolean()).isFalse();

        JsonNode second = rows.get(classList.get(1).getId());  // A, A, P, A -> 1/4
        assertThat(second.get("attendancePercentage").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(second.get("belowThreshold").asBoolean()).isTrue();
        assertThat(second.get("shortagePercentagePoints").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(second.get("minimumRequiredPercentage").decimalValue()).isEqualByComparingTo("75");

        JsonNode third = rows.get(classList.get(2).getId());   // all EXCUSED -> no denominator
        assertThat(third.get("excused").asLong()).isEqualTo(4);
        assertThat(third.get("attendancePercentage").isNull()).isTrue();
        assertThat(third.get("belowThreshold").asBoolean()).isFalse();
        assertThat(summary.get("studentsBelowThreshold").asInt()).isEqualTo(1);
    }

    @Test
    void studentSeesOnlyOwnAttendanceAndShortageNoticeIsDeduplicated() throws Exception {
        SubjectOffering own = offering(subject(Subject.SubjectType.THEORY), otherSection, staff);
        StudentLogin me = studentLogin(otherSection);
        Student classmate = student(otherSection);
        enroll(own, me.student());
        enroll(own, classmate);

        for (int day = 5; day >= 4; day--) {
            long id = data(postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", own.getId(),
                    "attendanceDate", LocalDate.now().minusDays(day).toString())).andExpect(status().isCreated())).get("id").asLong();
            putAs(staff, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(
                    mark(me.student().getId(), "ABSENT"), mark(classmate.getId(), "PRESENT")))).andExpect(status().isOk());
            postAs(staff, "/attendance/sessions/" + id + "/finalize", null).andExpect(status().isOk());
        }

        JsonNode mine = data(getAs(me.user(), "/attendance/me").andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("subjectOfferingId").asLong()).isEqualTo(own.getId());
        assertThat(mine.get(0).get("absent").asLong()).isEqualTo(2);
        assertThat(mine.get(0).get("belowThreshold").asBoolean()).isTrue();

        // Two finalizations below the minimum within the cooldown -> exactly one shortage notification.
        long notices = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(me.user().getId()) && "ATTENDANCE_SHORTAGE".equals(n.getReferenceType()))
                .count();
        assertThat(notices).isEqualTo(1);

        getAs(me.user(), "/attendance/sessions").andExpect(status().isForbidden());
        getAs(me.user(), "/attendance/subject-offerings/" + own.getId() + "/summary").andExpect(status().isForbidden());
    }

    @Test
    void hodReadsEverythingButCannotMark() throws Exception {
        long id = createSession(LocalDate.now());
        getAs(hod, "/attendance/sessions?subjectOfferingId=" + offering.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        getAs(hod, "/attendance/sessions/" + id + "/records").andExpect(status().isOk());
        getAs(hod, "/attendance/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isOk());
        putAs(hod, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(mark(classList.get(0).getId(), "PRESENT"))))
                .andExpect(status().isForbidden());
        postAs(hod, "/attendance/sessions/" + id + "/finalize", null).andExpect(status().isForbidden());
    }

    @Test
    void otherStaffCannotReadSessionsOfOfferingsNotAssignedToThem() throws Exception {
        long id = createSession(LocalDate.now());
        getAs(otherStaff, "/attendance/sessions/" + id).andExpect(status().isForbidden());
        getAs(otherStaff, "/attendance/subject-offerings/" + offering.getId() + "/summary").andExpect(status().isForbidden());
    }
}
