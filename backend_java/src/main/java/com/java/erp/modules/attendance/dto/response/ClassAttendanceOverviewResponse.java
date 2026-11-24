package com.java.erp.modules.attendance.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Class attendance per subject offering plus Overall, over FINALIZED sessions and currently ENROLLED students.
 * averageAttendancePercentage = attendedAttendanceRecords / eligibleAttendanceRecords * 100 for the whole class
 * (record-weighted, never the mean of student percentages); null when nothing is eligible yet.
 * studentsBelowThreshold counts students whose own percentage is below minimumPercentage (Overall: across all
 * the listed subjects together).
 */
public record ClassAttendanceOverviewResponse(
        Long batchId,
        Integer semester,
        Long sectionId,
        BigDecimal minimumPercentage,
        List<SubjectRow> subjects,
        OverallRow overall
) {
    public record SubjectRow(
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
            long classesConducted,
            long studentCount,
            long presentRecords,
            long absentRecords,
            long onDutyRecords,
            long excusedRecords,
            long eligibleAttendanceRecords,
            long attendedAttendanceRecords,
            BigDecimal averageAttendancePercentage,
            long studentsBelowThreshold,
            BigDecimal minimumPercentage
    ) {
    }

    public record OverallRow(
            int subjectCount,
            long classesConducted,
            long studentCount,
            long presentRecords,
            long absentRecords,
            long onDutyRecords,
            long excusedRecords,
            long eligibleAttendanceRecords,
            long attendedAttendanceRecords,
            BigDecimal averageAttendancePercentage,
            long studentsBelowThreshold,
            BigDecimal minimumPercentage
    ) {
    }
}
