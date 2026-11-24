package com.java.erp.modules.attendance.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;
import java.util.List;

/** One row per ENROLLED student of a subject offering (FINALIZED sessions only). */
public record ClassAttendanceSummaryResponse(
        Long subjectOfferingId,
        String subjectCode,
        String subjectName,
        Long sectionId,
        String sectionName,
        long finalizedSessions,
        BigDecimal minimumRequiredPercentage,
        int studentsBelowThreshold,
        List<Row> students
) {
    public record Row(
            Long studentId,
            String registerNumber,
            String studentName,
            @JsonUnwrapped AttendanceStats stats
    ) {
    }
}
