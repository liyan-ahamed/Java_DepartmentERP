package com.java.erp.modules.gradebook.dto.request;

import com.java.erp.modules.gradebook.entity.StudentGrade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rows read from a spreadsheet (the frontend parses the Excel / CSV file and sends JSON).
 * Per row: status null + marks -> GRADED; ABSENT / EXEMPTED without marks; status null + marks null = blank row,
 * reported as SKIPPED (the stored grade is never cleared by an import). Marks are checked per row (range,
 * at most 2 decimals) so the preview can list every problem.
 * confirmOverwrite: apply only; must be true when any row would change an existing grade.
 */
public record GradeImportRequest(
        @NotEmpty @Size(max = 500) List<@NotNull @Valid Row> rows,
        Boolean confirmOverwrite
) {
    /** rowNumber: the spreadsheet row, echoed in the response (defaults to the 1-based position in rows). */
    public record Row(
            @Min(1) Integer rowNumber,
            @NotBlank @Size(max = 30) String registerNumber,
            @DecimalMax("9999.99") BigDecimal marks,
            StudentGrade.GradeStatus status
    ) {
    }
}
