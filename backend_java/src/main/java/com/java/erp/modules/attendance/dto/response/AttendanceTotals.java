package com.java.erp.modules.attendance.dto.response;

import java.math.BigDecimal;

/**
 * Attendance counts over FINALIZED sessions (AttendancePolicy):
 *   eligibleClasses = present + absent + onDuty (EXCUSED is left out), attendedClasses = present + onDuty,
 *   attendancePercentage = attended / eligible * 100 (2 dp), null when eligible = 0.
 * totalClasses = every recorded class (including EXCUSED).
 */
public record AttendanceTotals(
        long totalClasses,
        long present,
        long absent,
        long onDuty,
        long excused,
        long eligibleClasses,
        long attendedClasses,
        BigDecimal attendancePercentage,
        BigDecimal minimumPercentage,
        boolean belowThreshold
) {
    public static AttendanceTotals of(AttendanceStats s) {
        return new AttendanceTotals(s.totalSessions(), s.present(), s.absent(), s.onDuty(), s.excused(), s.eligible(),
                s.attended(), s.attendancePercentage(), s.minimumRequiredPercentage(), s.belowThreshold());
    }
}
