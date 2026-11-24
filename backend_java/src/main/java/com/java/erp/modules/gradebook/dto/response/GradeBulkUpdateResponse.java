package com.java.erp.modules.gradebook.dto.response;

/**
 * Result of a bulk grade save or import. Deliberately contains counts only: it is also the audit
 * payload, and audit rows must not carry every student's marks.
 * changedGradeCount = createdCount + updatedCount + clearedCount.
 */
public record GradeBulkUpdateResponse(
        Long assessmentId,
        Long subjectOfferingId,
        int submittedCount,
        int createdCount,
        int updatedCount,
        int changedGradeCount,
        int clearedCount
) {
}
