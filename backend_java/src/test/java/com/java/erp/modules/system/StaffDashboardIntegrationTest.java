package com.java.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.repository.HolidayRepository;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.support.ErpModulesTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /dashboard/staff (upcoming classes, attendance + marks-entry tasks) and the marks-entry deadline endpoint. */
@TestPropertySource(properties = "features.gradebook.enabled=true")
class StaffDashboardIntegrationTest extends ErpModulesTestBase {

    @Autowired private HolidayRepository holidayRepository;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private StudentGradeRepository gradeRepository;

    private static final List<String> WEEKDAYS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");

    private void slot(SubjectOffering o, String day, int period) throws Exception {
        postAs(hod, "/timetable", Map.of("subjectOfferingId", o.getId(), "dayOfWeek", day, "periodNumber", period))
                .andExpect(status().isCreated());
    }

    private Assessment assessment(SubjectOffering o, String title) {
        Assessment a = new Assessment();
        a.setSubjectOffering(o);
        a.setCreatedBy(o.getStaff());
        a.setTitle(title);
        a.setAssessmentType(Assessment.AssessmentType.CAT_1);
        a.setMaxMarks(BigDecimal.valueOf(50));
        a.setAssessmentDate(LocalDate.of(2026, 9, 15));
        return assessmentRepository.saveAndFlush(a);
    }

    private void grade(Assessment a, Student s, StudentGrade.GradeStatus status) {
        StudentGrade g = new StudentGrade(a, s);
        g.setGradeStatus(status);
        if (status == StudentGrade.GradeStatus.GRADED) g.setMarks(BigDecimal.valueOf(40));
        gradeRepository.saveAndFlush(g);
    }

    private void deadline(Assessment a, OffsetDateTime dueAt) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("marksEntryDueAt", dueAt == null ? null : dueAt.toString());
        putAs(hod, "/assessments/" + a.getId() + "/marks-entry-deadline", body).andExpect(status().isOk());
    }

    private List<JsonNode> tasks(String type) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        data(getAs(staff, "/dashboard/staff").andExpect(status().isOk())).get("thingsToComplete")
                .forEach(t -> { if (t.get("type").asText().equals(type)) result.add(t); });
        return result;
    }

    @Test
    void upcomingClassesAreOwnChronologicalAndSkipHolidays() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        for (String day : WEEKDAYS) {
            slot(own, day, 1);
            slot(own, day, 6);
            slot(foreign, day, 2);
        }
        LocalDate holiday = LocalDate.now().plusDays(1);
        while (holiday.getDayOfWeek() == DayOfWeek.SATURDAY || holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            holiday = holiday.plusDays(1);
        }
        holidayRepository.saveAndFlush(new Holiday(holiday, "Test Closure", Holiday.Scope.COLLEGE, "test", LocalDateTime.now()));
        publishHolidayCalendar(holiday.getYear());

        JsonNode dashboard = data(getAs(staff, "/dashboard/staff").andExpect(status().isOk()));
        assertThat(dashboard.get("horizonDays").asInt()).isEqualTo(14);
        assertThat(dashboard.get("termDatesConfigured").asBoolean()).isFalse();
        JsonNode upcoming = dashboard.get("upcomingClasses");
        assertThat(upcoming).isNotEmpty();
        LocalDateTime previous = LocalDateTime.MIN;
        LocalDateTime now = LocalDateTime.now().minusMinutes(1);
        for (JsonNode c : upcoming) {
            assertThat(c.get("subjectOfferingId").asLong()).isEqualTo(own.getId());
            LocalDate date = LocalDate.parse(c.get("date").asText());
            LocalDateTime start = date.atTime(LocalTime.parse(c.get("startTime").asText()));
            assertThat(start).isAfterOrEqualTo(previous).isAfter(now);
            assertThat(date).isNotEqualTo(holiday).isBefore(LocalDate.now().plusDays(14));
            assertThat(c.get("sectionName").asText()).isEqualTo("A");
            previous = start;
        }
        String holidayText = holiday.toString();
        assertThat(dashboard.get("skippedHolidays")).anyMatch(h -> h.get("date").asText().equals(holidayText)
                && h.get("name").asText().equals("Test Closure"));
        // Two own classes on every other weekday of the coming week are listed.
        LocalDate nextWorkday = holiday.plusDays(1);
        while (nextWorkday.getDayOfWeek() == DayOfWeek.SATURDAY || nextWorkday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            nextWorkday = nextWorkday.plusDays(1);
        }
        String nextText = nextWorkday.toString();
        assertThat(upcoming).filteredOn(c -> c.get("date").asText().equals(nextText)).hasSize(2);

        getAs(hod, "/dashboard/staff").andExpect(status().isForbidden());
        getAs(studentLogin(section).user(), "/dashboard/staff").andExpect(status().isForbidden());
    }

    @Test
    void draftAttendanceSessionsBecomeTasks() throws Exception {
        SubjectOffering own = theoryOffering();
        enroll(own, student(section));
        long session = data(postAs(staff, "/attendance/sessions", Map.of("subjectOfferingId", own.getId(),
                "attendanceDate", "2026-10-05")).andExpect(status().isCreated())).get("id").asLong();
        List<JsonNode> tasks = tasks("ATTENDANCE_FINALIZATION");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).get("attendanceSessionId").asLong()).isEqualTo(session);
        assertThat(tasks.get(0).get("attendanceDate").asText()).isEqualTo("2026-10-05");
        assertThat(tasks.get(0).get("subjectOfferingId").asLong()).isEqualTo(own.getId());
        assertThat(data(getAs(otherStaff, "/dashboard/staff")).get("thingsToComplete")).isEmpty();
    }

    @Test
    void marksEntryTasksFollowRemainingCountAndDeadline() throws Exception {
        SubjectOffering own = theoryOffering();
        List<Student> classList = students(section, 3);
        Assessment cat = assessment(own, "CAT 1");
        grade(cat, classList.get(0), StudentGrade.GradeStatus.GRADED);

        assertThat(tasks("MARKS_ENTRY")).isEmpty();            // no deadline -> no reminder

        OffsetDateTime due = OffsetDateTime.now().plusHours(72).truncatedTo(ChronoUnit.SECONDS);
        deadline(cat, due);
        List<JsonNode> tasks = tasks("MARKS_ENTRY");
        assertThat(tasks).hasSize(1);
        JsonNode task = tasks.get(0);
        assertThat(task.get("title").asText()).isEqualTo("CAT 1 Marks Entry Incomplete");
        assertThat(task.get("assessmentId").asLong()).isEqualTo(cat.getId());
        assertThat(task.get("assessmentType").asText()).isEqualTo("CAT_1");
        assertThat(task.get("subjectCode").asText()).isEqualTo(own.getSubject().getCode());
        assertThat(task.get("sectionName").asText()).isEqualTo("A");
        assertThat(task.get("expectedCount").asInt()).isEqualTo(3);
        assertThat(task.get("remainingCount").asInt()).isEqualTo(2);
        assertThat(OffsetDateTime.parse(task.get("dueAt").asText()).toInstant()).isEqualTo(due.toInstant());
        assertThat(task.get("status").asText()).isEqualTo("PENDING");

        deadline(cat, OffsetDateTime.now().plusHours(24));
        assertThat(tasks("MARKS_ENTRY").get(0).get("status").asText()).isEqualTo("DUE_SOON");
        deadline(cat, OffsetDateTime.now().minusHours(1));
        assertThat(tasks("MARKS_ENTRY").get(0).get("status").asText()).isEqualTo("OVERDUE");

        grade(cat, classList.get(1), StudentGrade.GradeStatus.ABSENT);
        assertThat(tasks("MARKS_ENTRY").get(0).get("remainingCount").asInt()).isEqualTo(1);
        grade(cat, classList.get(2), StudentGrade.GradeStatus.EXEMPTED);
        assertThat(tasks("MARKS_ENTRY")).isEmpty();             // complete -> gone
    }

    @Test
    void finalizedAndForeignAssessmentsGiveNoTask() throws Exception {
        SubjectOffering own = theoryOffering();
        SubjectOffering foreign = offering(subject(Subject.SubjectType.THEORY), otherSection, otherStaff);
        students(section, 2);
        students(otherSection, 2);
        Assessment finalized = assessment(own, "Quiz");
        deadline(finalized, OffsetDateTime.now().plusDays(1));
        assessmentRepository.finalizeIfDraft(finalized.getId(), LocalDateTime.now(), Assessment.Status.DRAFT,
                Assessment.Status.FINALIZED);
        Assessment theirs = assessment(foreign, "CAT 2");
        deadline(theirs, OffsetDateTime.now().plusDays(1));

        assertThat(tasks("MARKS_ENTRY")).isEmpty();
        assertThat(data(getAs(otherStaff, "/dashboard/staff")).get("thingsToComplete"))
                .anyMatch(t -> t.get("assessmentId").asLong() == theirs.getId());
    }

    @Test
    void onlyHodSetsTheDeadlineAndNotAfterFinalization() throws Exception {
        SubjectOffering own = theoryOffering();
        Assessment cat = assessment(own, "CAT 1");
        putAs(staff, "/assessments/" + cat.getId() + "/marks-entry-deadline", Map.of("marksEntryDueAt", "2026-10-20T17:00:00+05:30"))
                .andExpect(status().isForbidden());
        JsonNode updated = data(putAs(hod, "/assessments/" + cat.getId() + "/marks-entry-deadline",
                Map.of("marksEntryDueAt", "2026-10-20T17:00:00+05:30")).andExpect(status().isOk()));
        assertThat(OffsetDateTime.parse(updated.get("marksEntryDueAt").asText()).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-20T17:00:00+05:30").toInstant());
        // The assigned staff member reads it on the assessment; finalizing stays a staff-only action.
        assertThat(data(getAs(staff, "/assessments/" + cat.getId())).get("marksEntryDueAt").isNull()).isFalse();
        postAs(hod, "/assessments/" + cat.getId() + "/finalize", null).andExpect(status().isForbidden());

        deadline(cat, null);
        assertThat(data(getAs(staff, "/assessments/" + cat.getId())).get("marksEntryDueAt").isNull()).isTrue();
        assessmentRepository.finalizeIfDraft(cat.getId(), LocalDateTime.now(), Assessment.Status.DRAFT, Assessment.Status.FINALIZED);
        putAs(hod, "/assessments/" + cat.getId() + "/marks-entry-deadline", Map.of("marksEntryDueAt", "2026-10-20T17:00:00+05:30"))
                .andExpect(status().isConflict());
    }
}
