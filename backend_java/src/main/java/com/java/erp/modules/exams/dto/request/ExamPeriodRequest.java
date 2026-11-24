package com.java.erp.modules.exams.dto.request;

import com.java.erp.modules.exams.entity.ExamPeriod;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** While OPEN only name and registration dates may change; a CLOSED period cannot be edited. */
public record ExamPeriodRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "must look like 2026-2027") String academicYear,
        @NotNull @Min(1) @Max(8) Integer semester,
        @NotNull ExamPeriod.ExamType examType,
        @NotNull LocalDate registrationStart,
        @NotNull LocalDate registrationEnd
) {
}
