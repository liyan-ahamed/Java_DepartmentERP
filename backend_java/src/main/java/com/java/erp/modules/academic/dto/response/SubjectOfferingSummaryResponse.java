package com.java.erp.modules.academic.dto.response;

import java.math.BigDecimal;

/**
 * Headline figures of one subject offering, all read from authoritative data:
 *   enrollmentCount           - active students currently ENROLLED
 *   finalizedAttendanceSessions / attendancePercentage - FINALIZED sessions only, AttendancePolicy formula over
 *                               all their records (null when nothing is eligible yet)
 *   finalizedAssessmentCount / averageAssessmentPercentage - FINALIZED assessments; average of GRADED marks
 *                               as a percentage of each assessment's max marks (null when none)
 *   publishedResultCount / passCount / failCount - published exam results
 * No internal marks: their calculation is not configured.
 */
public record SubjectOfferingSummaryResponse(
        Long subjectOfferingId,
        long enrollmentCount,
        long finalizedAttendanceSessions,
        BigDecimal attendancePercentage,
        long finalizedAssessmentCount,
        BigDecimal averageAssessmentPercentage,
        long publishedResultCount,
        long passCount,
        long failCount
) {
}
