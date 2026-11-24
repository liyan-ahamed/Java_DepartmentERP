package com.java.erp.modules.gradebook.dto.request;

import com.java.erp.modules.gradebook.entity.StudentGrade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates, updates or clears many students' grades for one DRAFT assessment in one transaction. Send only the
 * rows that changed (autosave); rows identical to the stored grade are left untouched.
 */
public record BulkGradeRequest(
        @NotEmpty @Size(max = 500) List<@NotNull @Valid Entry> grades
) {
    /**
     * GRADED needs marks (0..maxMarks, at most 2 decimals); ABSENT and EXEMPTED must send marks as null.
     * status null + marks null clears the student's grade (back to NOT_GRADED).
     */
    public record Entry(
            @NotNull Long studentId,
            @Digits(integer = 4, fraction = 2) BigDecimal marks,
            StudentGrade.GradeStatus status,
            @Size(max = 255) String remarks
    ) {
    }
}
