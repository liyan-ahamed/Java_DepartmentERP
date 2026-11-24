package com.java.erp.modules.gradebook.service;

import com.java.erp.modules.gradebook.entity.StudentGrade.GradeStatus;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The one definition of a valid mark: GRADED needs marks between 0 and the assessment's max marks with at most
 * 2 decimal places (the column is NUMERIC(6,2)); ABSENT and EXEMPTED carry no marks. Absent is never a zero.
 */
public final class GradeRules {

    public static final int MAX_DECIMALS = 2;

    private GradeRules() {
    }

    /** The problem with this status + marks, if any (used by bulk save, import and finalization). */
    public static Optional<String> problem(GradeStatus status, BigDecimal marks, BigDecimal maxMarks) {
        if (status != GradeStatus.GRADED) {
            return marks == null ? Optional.empty() : Optional.of("marks must be empty when status is " + status);
        }
        if (marks == null) return Optional.of("marks are required when status is GRADED");
        if (marks.signum() < 0) return Optional.of("marks cannot be negative");
        if (marks.stripTrailingZeros().scale() > MAX_DECIMALS) {
            return Optional.of("marks can have at most " + MAX_DECIMALS + " decimal places");
        }
        if (marks.compareTo(maxMarks) > 0) {
            return Optional.of("marks cannot exceed max marks (" + maxMarks.stripTrailingZeros().toPlainString() + ")");
        }
        return Optional.empty();
    }
}
