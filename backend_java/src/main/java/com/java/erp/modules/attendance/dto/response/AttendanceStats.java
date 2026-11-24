package com.java.erp.modules.attendance.dto.response;

import java.math.BigDecimal;

/**
 * Attendance figures over FINALIZED sessions (see AttendancePolicy). attendancePercentage and
 * shortagePercentagePoints are null when there is nothing to count (eligible = 0).
 */
public record AttendanceStats(
        long totalSessions,
        long present,
        long absent,
        long onDuty,
        long excused,
        long attended,
        long eligible,
        BigDecimal attendancePercentage,
        BigDecimal minimumRequiredPercentage,
        boolean belowThreshold,
        BigDecimal shortagePercentagePoints
) {
}
