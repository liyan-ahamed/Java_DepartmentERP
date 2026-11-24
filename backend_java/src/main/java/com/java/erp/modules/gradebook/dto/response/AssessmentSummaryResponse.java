package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.gradebook.entity.Assessment;

import java.math.BigDecimal;

/**
 * Basic statistics of one assessment over its expected class list. average/highest/lowest and
 * pass/fail use GRADED marks only (null when nothing is graded). Pass fields are null when the
 * assessment has no pass marks.
 */
public record AssessmentSummaryResponse(
        Long assessmentId,
        String title,
        Assessment.Status status,
        BigDecimal maxMarks,
        BigDecimal passMarks,
        int expectedStudentCount,
        int gradedCount,
        int absentCount,
        int exemptedCount,
        int notGradedCount,
        BigDecimal average,
        BigDecimal highest,
        BigDecimal lowest,
        Integer passCount,
        Integer failCount,
        BigDecimal passPercentage
) {
}
