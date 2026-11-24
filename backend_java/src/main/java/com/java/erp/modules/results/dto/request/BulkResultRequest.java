package com.java.erp.modules.results.dto.request;

import com.java.erp.modules.results.entity.ExamResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates or updates unpublished results of one subject offering in one exam period (all or nothing).
 * PASS / FAIL need marksObtained; ABSENT has no marks and no grade; WITHHELD may keep marks.
 * grade is a short label as entered (e.g. O, A+, B, RA); no GPA is derived from it.
 */
public record BulkResultRequest(
        @NotEmpty @Size(max = 500) List<@NotNull @Valid Entry> results
) {
    public record Entry(
            @NotNull Long studentId,
            @Digits(integer = 4, fraction = 2) BigDecimal marksObtained,
            @NotNull @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal maxMarks,
            @Pattern(regexp = "^[A-Za-z+\\-]{1,5}$", message = "must be 1-5 letters, + or -") String grade,
            @NotNull ExamResult.ResultStatus resultStatus,
            @Size(max = 255) String remarks
    ) {
    }
}
