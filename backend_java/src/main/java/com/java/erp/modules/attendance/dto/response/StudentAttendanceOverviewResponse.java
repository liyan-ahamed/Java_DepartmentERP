package com.java.erp.modules.attendance.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;
import java.util.List;

/**
 * The logged-in student's attendance per active enrolled subject plus Overall. Overall is computed from the
 * summed counts of all subjects (never an average of subject percentages).
 */
public record StudentAttendanceOverviewResponse(
        BigDecimal minimumPercentage,
        List<Subject> subjects,
        AttendanceTotals overall
) {
    public record Subject(
            Long subjectOfferingId,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String academicYear,
            Integer semester,
            String facultyName,
            @JsonUnwrapped AttendanceTotals totals
    ) {
    }
}
