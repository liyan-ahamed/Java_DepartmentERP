package com.java.erp.modules.attendance.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** One subject of the logged-in student's attendance (FINALIZED sessions only). */
public record SubjectAttendanceResponse(
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String staffName,
        @JsonUnwrapped AttendanceStats stats
) {
}
