package com.java.erp.modules.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day view, student class status, subject + Overall summaries. Fixture (section A unless noted):
 *   A = offering of `staff`, Monday period 1;  B = offering of `otherStaff`, Monday period 2;
 *   C = offering of `otherStaff` in section B, Monday period 1. Students s1 (with login) and s2 in A and B.
 */
class AttendanceDayAndSummaryIntegrationTest extends ErpModulesTestBase {

    private static final String MONDAY = "2026-10-05";

    @Autowired private HolidayRepository holidayRepository;

    private SubjectOffering a, b, c;
    private long slotA, slotB;
    private StudentLogin s1;
    private Student s2;

    @BeforeEach
    void fixture() throws Exception {
        a = theoryOffering();
        b = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        c = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        slotA = slot(a, "MONDAY", 1);
        slotB = slot(b, "MONDAY", 2);
        slot(c, "MONDAY", 1);
        s1 = studentLogin(section);
        s2 = student(section);
        for (SubjectOffering o : List.of(a, b)) {
            enroll(o, s1.student());
            enroll(o, s2);
        }
        enroll(c, student(otherSection));
    }

    private long slot(SubjectOffering o, String day, int period) throws Exception {
        return data(postAs(hod, "/timetable", Map.of("subjectOfferingId", o.getId(), "dayOfWeek", day, "periodNumber", period))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Creates a session for the offering's own staff member, marks it and optionally finalizes it. */
    private long session(SubjectOffering o, String date, Long slotId, Map<Student, String> marks, boolean finalize) throws Exception {
        User owner = o.getStaff();
        Map<String, Object> body = new HashMap<>();
        body.put("subjectOfferingId", o.getId());
        body.put("attendanceDate", date);
        if (slotId != null) body.put("timetableSlotId", slotId);
        long id = data(postAs(owner, "/attendance/sessions", body).andExpect(status().isCreated())).get("id").asLong();
        List<Map<String, Object>> records = new ArrayList<>();
        marks.forEach((s, st) -> records.add(Map.of("studentId", s.getId(), "status", st)));
        putAs(owner, "/attendance/sessions/" + id + "/records", Map.of("records", records)).andExpect(status().isOk());
        if (finalize) postAs(owner, "/attendance/sessions/" + id + "/finalize", null).andExpect(status().isOk());
        return id;
    }

    private Map<Student, String> marks(String s1Status, String s2Status) {
        Map<Student, String> m = new LinkedHashMap<>();
        m.put(s1.student(), s1Status);
        m.put(s2, s2Status);
        return m;
    }

    // ---- Day view ----

    @Test
    void hodSeesEveryScheduledClassOfTheSelectedSection() throws Exception {
        long finalized = session(a, MONDAY, slotA, marks("PRESENT", "ABSENT"), true);
        JsonNode day = data(getAs(hod, "/attendance/day?date=" + MONDAY + "&batchId=" + batch.getId()
                + "&semester=3&sectionId=" + section.getId()).andExpect(status().isOk()));
        assertThat(day.get("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(day.get("holiday").asBoolean()).isFalse();
        JsonNode classes = day.get("classes");
        assertThat(classes).hasSize(2);
        JsonNode first = classes.get(0);
        assertThat(first.get("subjectOfferingId").asLong()).isEqualTo(a.getId());
        assertThat(first.get("periodNumber").asInt()).isEqualTo(1);
        assertThat(first.get("startTime").asText()).isEqualTo("08:15:00");
        assertThat(first.get("subjectCode").asText()).isEqualTo(a.getSubject().getCode());
        assertThat(first.get("sectionName").asText()).isEqualTo("A");
        assertThat(first.get("batchId").asLong()).isEqualTo(batch.getId());
        assertThat(first.get("facultyId").asLong()).isEqualTo(staff.getId());
        assertThat(first.get("attendanceSessionId").asLong()).isEqualTo(finalized);
        assertThat(first.get("attendanceSessionStatus").asText()).isEqualTo("FINALIZED");
        assertThat(first.get("myAttendanceStatus").isNull()).isTrue();
        assertThat(classes.get(1).get("subjectOfferingId").asLong()).isEqualTo(b.getId());
        assertThat(classes.get(1).get("attendanceSessionId").isNull()).isTrue();

        getAs(hod, "/attendance/day?date=" + MONDAY).andExpect(status().isBadRequest());
        getAs(hod, "/attendance/day?date=" + MONDAY + "&batchId=999999&sectionId=" + section.getId()).andExpect(status().isBadRequest());
        // Tuesday: nothing scheduled.
        assertThat(data(getAs(hod, "/attendance/day?date=2026-10-06&sectionId=" + section.getId())).get("classes")).isEmpty();
    }

    @Test
    void hodClassDetailReusesTheSessionRoster() throws Exception {
        long id = session(a, MONDAY, slotA, marks("PRESENT", "ON_DUTY"), true);
        JsonNode roster = data(getAs(hod, "/attendance/sessions/" + id + "/records").andExpect(status().isOk()));
        assertThat(roster.get("session").get("status").asText()).isEqualTo("FINALIZED");
        assertThat(roster.get("records")).hasSize(2).allSatisfy(r -> {
            assertThat(r.get("registerNumber").asText()).isNotBlank();
            assertThat(r.get("studentName").asText()).isNotBlank();
            assertThat(r.get("status").asText()).isIn("PRESENT", "ON_DUTY");
        });
        // HOD stays read-only.
        putAs(hod, "/attendance/sessions/" + id + "/records", Map.of("records", List.of(
                Map.of("studentId", s2.getId(), "status", "ABSENT")))).andExpect(status().isForbidden());
    }

    @Test
    void staffOnlyEverSeeTheirOwnClasses() throws Exception {
        JsonNode mine = data(getAs(staff, "/attendance/day?date=" + MONDAY + "&sectionId=" + otherSection.getId()));
        assertThat(mine.get("classes")).isEmpty();
        JsonNode own = data(getAs(staff, "/attendance/day?date=" + MONDAY));
        assertThat(own.get("classes")).hasSize(1);
        assertThat(own.get("classes").get(0).get("subjectOfferingId").asLong()).isEqualTo(a.getId());
        JsonNode other = data(getAs(otherStaff, "/attendance/day?date=" + MONDAY));
        assertThat(other.get("classes")).extracting(r -> r.get("subjectOfferingId").asLong())
                .containsExactlyInAnyOrder(b.getId(), c.getId());
    }

    @Test
    void studentsSeeOwnClassesWithOwnFinalizedStatusOnly() throws Exception {
        session(a, MONDAY, slotA, marks("PRESENT", "ABSENT"), true);
        session(b, MONDAY, slotB, marks("ABSENT", "PRESENT"), false);     // draft: not official yet
        JsonNode day = data(getAs(s1.user(), "/attendance/day?date=" + MONDAY + "&sectionId=" + otherSection.getId())
                .andExpect(status().isOk()));
        JsonNode classes = day.get("classes");
        assertThat(classes).hasSize(2);
        assertThat(classes.get(0).get("subjectOfferingId").asLong()).isEqualTo(a.getId());
        assertThat(classes.get(0).get("myAttendanceStatus").asText()).isEqualTo("PRESENT");
        assertThat(classes.get(1).get("subjectOfferingId").asLong()).isEqualTo(b.getId());
        assertThat(classes.get(1).get("myAttendanceStatus").asText()).isEqualTo("NOT_RECORDED");
        assertThat(classes.get(1).get("attendanceSessionId").isNull()).isTrue();
        assertThat(classes).noneMatch(r -> r.get("subjectOfferingId").asLong() == c.getId());

        getAs(s1.user(), "/attendance/summary?sectionId=" + section.getId()).andExpect(status().isForbidden());
        getAs(s1.user(), "/attendance/sessions/1/records").andExpect(status().isForbidden());
        mvc.perform(get("/attendance/day?date=" + MONDAY)).andExpect(status().isUnauthorized());
    }

    @Test
    void holidayDayIsFlaggedAndSlotsAreKept() throws Exception {
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2026, 10, 12), "Test Closure", Holiday.Scope.COLLEGE,
                "test", LocalDateTime.now()));
        publishHolidayCalendar(2026);
        JsonNode day = data(getAs(hod, "/attendance/day?date=2026-10-12&sectionId=" + section.getId()));
        assertThat(day.get("holiday").asBoolean()).isTrue();
        assertThat(day.get("holidayName").asText()).isEqualTo("Test Closure");
        assertThat(day.get("holidayCategory").asText()).isEqualTo("GENERAL_HOLIDAY");
        assertThat(day.get("classes")).hasSize(2).allSatisfy(r -> {
            assertThat(r.get("holiday").asBoolean()).isTrue();
            assertThat(r.get("attendanceSessionId").isNull()).isTrue();
        });
        // The recurring slots are untouched.
        getAs(hod, "/timetable/" + slotA).andExpect(status().isOk());
        assertThat(data(getAs(hod, "/attendance/day?date=" + MONDAY + "&sectionId=" + section.getId())).get("holiday").asBoolean())
                .isFalse();
    }

    // ---- Summaries ----

    /**
     * A: s1 P, P, E (100%); s2 A, OD, A (33.33%). Record-weighted class average = 3 attended / 5 eligible = 60.00
     * (the mean of student percentages would be 66.67). A DRAFT session must not count.
     * B: s1 A, s2 P. Overall: 4 attended / 7 eligible = 57.14; s1 overall 2/3, s2 2/4 -> both below 75.
     */
    private void attendanceHistory() throws Exception {
        session(a, "2026-09-07", null, marks("PRESENT", "ABSENT"), true);
        session(a, "2026-09-14", null, marks("PRESENT", "ON_DUTY"), true);
        session(a, "2026-09-21", null, marks("EXCUSED", "ABSENT"), true);
        session(a, "2026-09-28", null, marks("ABSENT", "ABSENT"), false);    // DRAFT: excluded
        session(b, "2026-09-07", null, marks("ABSENT", "PRESENT"), true);
    }

    @Test
    void classSummaryIsRecordWeightedWithOverall() throws Exception {
        attendanceHistory();
        JsonNode summary = data(getAs(hod, "/attendance/summary?batchId=" + batch.getId() + "&semester=3&sectionId=" + section.getId())
                .andExpect(status().isOk()));
        assertThat(summary.get("minimumPercentage").decimalValue()).isEqualByComparingTo("75");
        JsonNode subjects = summary.get("subjects");
        assertThat(subjects).hasSize(2);
        JsonNode rowA = subjects.get(0).get("subjectOfferingId").asLong() == a.getId() ? subjects.get(0) : subjects.get(1);
        assertThat(rowA.get("classesConducted").asLong()).isEqualTo(3);
        assertThat(rowA.get("studentCount").asLong()).isEqualTo(2);
        assertThat(rowA.get("presentRecords").asLong()).isEqualTo(2);
        assertThat(rowA.get("onDutyRecords").asLong()).isEqualTo(1);
        assertThat(rowA.get("excusedRecords").asLong()).isEqualTo(1);
        assertThat(rowA.get("eligibleAttendanceRecords").asLong()).isEqualTo(5);
        assertThat(rowA.get("attendedAttendanceRecords").asLong()).isEqualTo(3);
        assertThat(rowA.get("averageAttendancePercentage").decimalValue()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(rowA.get("studentsBelowThreshold").asLong()).isEqualTo(1);
        assertThat(rowA.get("facultyId").asLong()).isEqualTo(staff.getId());

        JsonNode overall = summary.get("overall");
        assertThat(overall.get("subjectCount").asInt()).isEqualTo(2);
        assertThat(overall.get("classesConducted").asLong()).isEqualTo(4);
        assertThat(overall.get("studentCount").asLong()).isEqualTo(2);
        assertThat(overall.get("eligibleAttendanceRecords").asLong()).isEqualTo(7);
        assertThat(overall.get("attendedAttendanceRecords").asLong()).isEqualTo(4);
        assertThat(overall.get("averageAttendancePercentage").decimalValue()).isEqualByComparingTo(new BigDecimal("57.14"));
        assertThat(overall.get("studentsBelowThreshold").asLong()).isEqualTo(2);

        getAs(hod, "/attendance/summary").andExpect(status().isBadRequest());
    }

    @Test
    void staffSummaryCoversOwnOfferingsOnly() throws Exception {
        attendanceHistory();
        JsonNode own = data(getAs(staff, "/attendance/summary"));
        assertThat(own.get("subjects")).hasSize(1);
        assertThat(own.get("subjects").get(0).get("subjectOfferingId").asLong()).isEqualTo(a.getId());
        assertThat(own.get("subjects").get(0).get("averageAttendancePercentage").decimalValue())
                .isEqualByComparingTo(new BigDecimal("60.00"));
        JsonNode other = data(getAs(otherStaff, "/attendance/summary?sectionId=" + section.getId()));
        assertThat(other.get("subjects")).extracting(r -> r.get("subjectOfferingId").asLong()).containsExactly(b.getId());
    }

    @Test
    void studentSummaryHasSubjectsAndCountBasedOverall() throws Exception {
        attendanceHistory();
        JsonNode mine = data(getAs(s1.user(), "/attendance/me/summary").andExpect(status().isOk()));
        Map<Long, JsonNode> bySubject = new HashMap<>();
        mine.get("subjects").forEach(sub -> bySubject.put(sub.get("subjectOfferingId").asLong(), sub));
        JsonNode subA = bySubject.get(a.getId());
        assertThat(subA.get("totalClasses").asLong()).isEqualTo(3);
        assertThat(subA.get("present").asLong()).isEqualTo(2);
        assertThat(subA.get("excused").asLong()).isEqualTo(1);
        assertThat(subA.get("eligibleClasses").asLong()).isEqualTo(2);
        assertThat(subA.get("attendedClasses").asLong()).isEqualTo(2);
        assertThat(subA.get("attendancePercentage").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(subA.get("belowThreshold").asBoolean()).isFalse();
        JsonNode subB = bySubject.get(b.getId());
        assertThat(subB.get("attendancePercentage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(subB.get("belowThreshold").asBoolean()).isTrue();

        JsonNode overall = mine.get("overall");
        assertThat(overall.get("totalClasses").asLong()).isEqualTo(4);
        assertThat(overall.get("eligibleClasses").asLong()).isEqualTo(3);
        assertThat(overall.get("attendedClasses").asLong()).isEqualTo(2);
        // 2 / 3 from counts, not the 50.00 average of 100.00 and 0.00.
        assertThat(overall.get("attendancePercentage").decimalValue()).isEqualByComparingTo("66.67");
        assertThat(overall.get("minimumPercentage").decimalValue()).isEqualByComparingTo("75");
        assertThat(overall.get("belowThreshold").asBoolean()).isTrue();

        getAs(staff, "/attendance/me/summary").andExpect(status().isForbidden());
    }

    @Test
    void noFinalizedSessionsMeansNullPercentages() throws Exception {
        JsonNode mine = data(getAs(s1.user(), "/attendance/me/summary"));
        assertThat(mine.get("overall").get("attendancePercentage").isNull()).isTrue();
        JsonNode summary = data(getAs(hod, "/attendance/summary?sectionId=" + section.getId()));
        assertThat(summary.get("overall").get("averageAttendancePercentage").isNull()).isTrue();
        assertThat(summary.get("overall").get("studentsBelowThreshold").asLong()).isZero();
    }
}
