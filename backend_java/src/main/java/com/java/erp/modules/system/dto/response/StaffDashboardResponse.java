package com.java.erp.modules.system.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The logged-in staff member's own dashboard (no department-wide figures).
 *
 * upcomingClasses: own classes from the recurring timetable over the next horizonDays days, in time order,
 *   starting now; dates that are institution holidays are left out (listed in skippedHolidays).
 *   termDatesConfigured = false: the ERP stores no term start/end dates yet, so the window is simply horizonDays.
 * thingsToComplete: computed tasks (nothing is stored):
 *   ATTENDANCE_FINALIZATION - a DRAFT attendance session of an own offering;
 *   MARKS_ENTRY - a DRAFT assessment with a marks-entry deadline whose class list still has unmarked students
 *     (status OVERDUE after the deadline, DUE_SOON within dueSoonHours of it, else PENDING).
 */
public record StaffDashboardResponse(
        LocalDateTime generatedAt,
        int horizonDays,
        boolean termDatesConfigured,
        int dueSoonHours,
        List<UpcomingClass> upcomingClasses,
        List<SkippedHoliday> skippedHolidays,
        List<Task> thingsToComplete
) {
    public record UpcomingClass(
            LocalDate date,
            DayOfWeek dayOfWeek,
            Integer periodNumber,
            LocalTime startTime,
            LocalTime endTime,
            Long timetableSlotId,
            Long subjectOfferingId,
            String subjectCode,
            String subjectName,
            Long sectionId,
            String sectionName,
            Long batchId,
            String batchName,
            Integer semester,
            String room
    ) {
    }

    public record SkippedHoliday(LocalDate date, String name) {
    }

    /** Fields that do not apply to a task type are null. */
    public record Task(
            String type,
            String title,
            String status,
            Long subjectOfferingId,
            String subjectCode,
            String subjectName,
            Long sectionId,
            String sectionName,
            Long attendanceSessionId,
            LocalDate attendanceDate,
            Integer periodNumber,
            Long assessmentId,
            String assessmentName,
            String assessmentType,
            OffsetDateTime dueAt,
            Integer expectedCount,
            Integer remainingCount
    ) {
    }
}
