package com.java.erp.modules.timetable;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Official period schedule, period validation, weekly retrieval and holiday annotation of the timetable. */
class TimetablePeriodIntegrationTest extends ErpModulesTestBase {

    @Autowired private HolidayRepository holidayRepository;

    private static Map<String, Object> period(Long offeringId, String day, Integer periodNumber) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectOfferingId", offeringId);
        m.put("dayOfWeek", day);
        m.put("periodNumber", periodNumber);
        return m;
    }

    private static Map<String, Object> timed(Long offeringId, String day, String start, String end) {
        Map<String, Object> m = period(offeringId, day, null);
        m.put("startTime", start);
        m.put("endTime", end);
        return m;
    }

    private long create(SubjectOffering o, String day, int periodNumber) throws Exception {
        return data(postAs(hod, "/timetable", period(o.getId(), day, periodNumber)).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void everyRoleReadsTheOfficialPeriodSchedule() throws Exception {
        StudentLogin student = studentLogin(section);
        for (var user : new com.java.erp.modules.auth.entity.User[]{hod, staff, student.user()}) {
            JsonNode periods = data(getAs(user, "/timetable/periods").andExpect(status().isOk()));
            assertThat(periods).hasSize(10);
            assertThat(periods.get(0).get("periodNumber").asInt()).isEqualTo(1);
            assertThat(periods.get(0).get("startTime").asText()).isEqualTo("08:15:00");
            assertThat(periods.get(2).get("type").asText()).isEqualTo("BREAK");
            assertThat(periods.get(2).get("periodNumber").isNull()).isTrue();
            assertThat(periods.get(6).get("type").asText()).isEqualTo("LUNCH");
            assertThat(periods.get(9).get("endTime").asText()).isEqualTo("15:45:00");
        }
    }

    @Test
    void periodNumberAloneCreatesASlotWithOfficialTimes() throws Exception {
        SubjectOffering offering = theoryOffering();
        postAs(hod, "/timetable", period(offering.getId(), "MONDAY", 6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.periodNumber").value(6))
                .andExpect(jsonPath("$.data.startTime").value("13:30:00"))
                .andExpect(jsonPath("$.data.endTime").value("14:15:00"));
        // Matching times are accepted too; times alone resolve to their period.
        postAs(hod, "/timetable", timed(offering.getId(), "TUESDAY", "15:00", "15:45"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.periodNumber").value(8));
    }

    @Test
    void breakLunchUnofficialTimesAndInvalidPeriodsAreRejected() throws Exception {
        SubjectOffering offering = theoryOffering();
        postAs(hod, "/timetable", timed(offering.getId(), "MONDAY", "09:55", "10:10")).andExpect(status().isBadRequest());
        postAs(hod, "/timetable", timed(offering.getId(), "MONDAY", "12:40", "13:30")).andExpect(status().isBadRequest());
        postAs(hod, "/timetable", timed(offering.getId(), "MONDAY", "09:00", "09:50")).andExpect(status().isBadRequest());
        postAs(hod, "/timetable", period(offering.getId(), "MONDAY", 9)).andExpect(status().isBadRequest());
        postAs(hod, "/timetable", period(offering.getId(), "MONDAY", 0)).andExpect(status().isBadRequest());
        postAs(hod, "/timetable", period(offering.getId(), "MONDAY", null)).andExpect(status().isBadRequest());
        Map<String, Object> mismatch = timed(offering.getId(), "MONDAY", "08:15", "09:05");
        mismatch.put("periodNumber", 2);
        postAs(hod, "/timetable", mismatch).andExpect(status().isBadRequest());
        Map<String, Object> half = period(offering.getId(), "MONDAY", 1);
        half.put("startTime", "08:15");
        postAs(hod, "/timetable", half).andExpect(status().isBadRequest());
        getAs(hod, "/timetable?subjectOfferingId=" + offering.getId()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void samePeriodConflictsForSectionAndStaff() throws Exception {
        SubjectOffering a = theoryOffering();
        SubjectOffering sameSection = offering(subject(Subject.SubjectType.THEORY), section, otherStaff);
        SubjectOffering sameStaff = offering(subject(Subject.SubjectType.THEORY), otherSection, staff);
        create(a, "THURSDAY", 3);
        postAs(hod, "/timetable", period(sameSection.getId(), "THURSDAY", 3)).andExpect(status().isConflict());
        postAs(hod, "/timetable", period(sameStaff.getId(), "THURSDAY", 3)).andExpect(status().isConflict());
        create(sameSection, "THURSDAY", 4);
        create(sameStaff, "THURSDAY", 4);
    }

    @Test
    void hodGetsTheWholeWeekFilteredByCohortSemesterSectionAndFaculty() throws Exception {
        SubjectOffering a = theoryOffering();                                            // section A, staff, semester 3
        SubjectOffering b = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        create(a, "MONDAY", 1);
        create(a, "WEDNESDAY", 5);
        create(b, "MONDAY", 1);

        JsonNode week = data(getAs(hod, "/timetable?batchId=" + batch.getId() + "&semester=3&sectionId=" + section.getId())
                .andExpect(status().isOk()));
        assertThat(week).hasSize(2);
        JsonNode slot = week.get(0);
        assertThat(slot.get("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(slot.get("periodNumber").asInt()).isEqualTo(1);
        assertThat(slot.get("startTime").asText()).isEqualTo("08:15:00");
        assertThat(slot.get("endTime").asText()).isEqualTo("09:05:00");
        assertThat(slot.get("subjectOfferingId").asLong()).isEqualTo(a.getId());
        assertThat(slot.get("subjectId").asLong()).isEqualTo(a.getSubject().getId());
        assertThat(slot.get("subjectCode").asText()).isEqualTo(a.getSubject().getCode());
        assertThat(slot.get("subjectName").asText()).isEqualTo(a.getSubject().getName());
        assertThat(slot.get("sectionId").asLong()).isEqualTo(section.getId());
        assertThat(slot.get("sectionName").asText()).isEqualTo("A");
        assertThat(slot.get("batchId").asLong()).isEqualTo(batch.getId());
        assertThat(slot.get("batchName").asText()).isEqualTo(batch.getBatchName());
        assertThat(slot.get("semester").asInt()).isEqualTo(3);
        assertThat(slot.get("staffId").asLong()).isEqualTo(staff.getId());
        assertThat(slot.get("staffName").asText()).isEqualTo(staff.getName());
        assertThat(slot.get("date").isNull()).isTrue();

        getAs(hod, "/timetable?batchId=" + batch.getId() + "&semester=5").andExpect(jsonPath("$.data.length()").value(0));
        getAs(hod, "/timetable?batchId=" + batch.getId() + "&staffId=" + otherStaff.getId())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subjectOfferingId").value(b.getId()));
    }

    @Test
    void weekOfAddsDatesAndHolidaysWithoutChangingTheRecurringSlots() throws Exception {
        SubjectOffering a = theoryOffering();
        long monday = create(a, "MONDAY", 1);
        create(a, "TUESDAY", 2);
        create(a, "WEDNESDAY", 3);
        LocalDateTime now = LocalDateTime.now();
        // Government lists are reference only: they never mark a class as a holiday.
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2026, 10, 20), "Dussehra", Holiday.Scope.NATIONAL, "test", now));
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2026, 10, 21), "State Day", Holiday.Scope.TAMIL_NADU, "test", now));
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2026, 10, 19), "Ayutha Pooja", Holiday.Scope.COLLEGE, "test", now));
        holidayRepository.saveAndFlush(new Holiday(LocalDate.of(2026, 10, 20), "Vijaya Dasami", Holiday.Scope.COLLEGE, "test", now));
        publishHolidayCalendar(2026);

        JsonNode week = data(getAs(hod, "/timetable?subjectOfferingId=" + a.getId() + "&weekOf=2026-10-21").andExpect(status().isOk()));
        assertThat(week).hasSize(3);
        assertThat(week.get(0).get("date").asText()).isEqualTo("2026-10-19");
        assertThat(week.get(0).get("isHoliday").asBoolean()).isTrue();
        assertThat(week.get(0).get("holidayName").asText()).isEqualTo("Ayutha Pooja");
        assertThat(week.get(0).get("holidayScope").asText()).isEqualTo("COLLEGE");
        assertThat(week.get(1).get("date").asText()).isEqualTo("2026-10-20");
        // Only the published college calendar counts; the central-list "Dussehra" on the same date is ignored.
        assertThat(week.get(1).get("holidayScope").asText()).isEqualTo("COLLEGE");
        assertThat(week.get(1).get("holidayName").asText()).isEqualTo("Vijaya Dasami");
        assertThat(week.get(2).get("date").asText()).isEqualTo("2026-10-21");
        assertThat(week.get(2).get("isHoliday").asBoolean()).isFalse();   // Tamil Nadu list only
        assertThat(week.get(2).get("holidayName").isNull()).isTrue();

        // The recurring slot is untouched by the holiday.
        JsonNode recurring = data(getAs(hod, "/timetable/" + monday).andExpect(status().isOk()));
        assertThat(recurring.get("active").asBoolean()).isTrue();
        assertThat(recurring.get("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(recurring.get("isHoliday").isNull()).isTrue();
        getAs(hod, "/timetable?subjectOfferingId=" + a.getId() + "&weekOf=2026-10-26")
                .andExpect(jsonPath("$.data[0].date").value("2026-10-26"))
                .andExpect(jsonPath("$.data[0].isHoliday").value(false));
    }

    @Test
    void staffAndStudentsSeeOnlyTheirOwnTimetable() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        create(own, "MONDAY", 1);
        create(own, "FRIDAY", 8);
        create(foreign, "MONDAY", 2);
        StudentLogin me = studentLogin(section);
        StudentLogin other = studentLogin(otherSection);
        enroll(own, me.student());
        enroll(foreign, other.student());

        JsonNode staffWeek = data(getAs(staff, "/timetable/me?weekOf=2026-10-12").andExpect(status().isOk()));
        assertThat(staffWeek).hasSize(2).allSatisfy(s -> assertThat(s.get("staffId").asLong()).isEqualTo(staff.getId()));
        assertThat(staffWeek.get(1).get("date").asText()).isEqualTo("2026-10-16");
        getAs(staff, "/timetable?sectionId=" + otherSection.getId()).andExpect(jsonPath("$.data.length()").value(0));

        JsonNode studentWeek = data(getAs(me.user(), "/timetable/me").andExpect(status().isOk()));
        assertThat(studentWeek).hasSize(2).allSatisfy(s -> assertThat(s.get("subjectOfferingId").asLong()).isEqualTo(own.getId()));
        getAs(other.user(), "/timetable/me").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subjectOfferingId").value(foreign.getId()));

        // Students cannot list or open other timetables, and cannot change any.
        getAs(me.user(), "/timetable?sectionId=" + otherSection.getId()).andExpect(status().isForbidden());
        getAs(me.user(), "/timetable/1").andExpect(status().isForbidden());
        postAs(me.user(), "/timetable", period(own.getId(), "TUESDAY", 1)).andExpect(status().isForbidden());
        postAs(staff, "/timetable", period(own.getId(), "TUESDAY", 1)).andExpect(status().isForbidden());
    }
}
