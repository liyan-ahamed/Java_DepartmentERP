package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.gradebook.entity.StudentGrade;

import java.math.BigDecimal;
import java.util.List;

/**
 * What an import would do; nothing is saved by the preview. canApply = no UNMATCHED / DUPLICATE / INVALID rows.
 * Applying still needs confirmOverwrite=true when overwriteCount > 0.
 * studentsNotInFile: class-list students that no row matched (their grades stay as they are).
 */
public record GradeImportPreviewResponse(
        Long assessmentId,
        BigDecimal maxMarks,
        int totalRows,
        int newCount,
        int overwriteCount,
        int unchangedCount,
        int skippedCount,
        int errorCount,
        boolean canApply,
        List<Row> rows,
        List<MissingStudent> studentsNotInFile
) {
    /**
     * NEW: no grade yet. OVERWRITE: replaces a different stored grade. UNCHANGED: same as stored. SKIPPED: blank row.
     * UNMATCHED: register number not in this class. DUPLICATE: register number in more than one row. INVALID: bad marks.
     */
    public enum Outcome { NEW, OVERWRITE, UNCHANGED, SKIPPED, UNMATCHED, DUPLICATE, INVALID }

    public record Row(
            int rowNumber,
            String registerNumber,
            Long studentId,
            String studentName,
            BigDecimal marks,
            StudentGrade.GradeStatus status,
            BigDecimal currentMarks,
            String currentStatus,
            Outcome outcome,
            String error
    ) {
    }

    public record MissingStudent(Long studentId, String registerNumber, String studentName, String currentStatus) {
    }
}
