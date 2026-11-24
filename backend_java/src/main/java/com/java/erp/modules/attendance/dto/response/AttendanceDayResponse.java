package com.java.erp.modules.attendance.dto.response;

import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.holiday.entity.Holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduled classes of one date (from the recurring timetable) with the attendance session recorded for each,
 * if any. On an effective holiday the day and every class carry holiday = true; slots are never removed.
 */
public record AttendanceDayResponse(
        LocalDate date,
        DayOfWeek dayOfWeek,
        boolean holiday,
        String holidayName,
        Holiday.Category holidayCategory,
        List<ClassRow> classes
) {
    /**
     * attendanceSessionId/Status: the session recorded for this class on this date (null = none yet; students
     * only see FINALIZED sessions). myAttendanceStatus (students only, else null): PRESENT, ABSENT, ON_DUTY,
     * EXCUSED from a FINALIZED session, otherwise NOT_RECORDED.
     */
    public record ClassRow(
            LocalDate date,
            DayOfWeek dayOfWeek,
            Integer periodNumber,
            LocalTime startTime,
            LocalTime endTime,
            Long timetableSlotId,
            String room,
            Long subjectOfferingId,
            Long subjectId,
            String subjectCode,
            String subjectName,
            Long sectionId,
            String sectionName,
            Long batchId,
            String batchName,
            Integer semester,
            Long facultyId,
            String facultyName,
            Long attendanceSessionId,
            AttendanceSession.Status attendanceSessionStatus,
            boolean holiday,
            String holidayName,
            String myAttendanceStatus
    ) {
    }
}
