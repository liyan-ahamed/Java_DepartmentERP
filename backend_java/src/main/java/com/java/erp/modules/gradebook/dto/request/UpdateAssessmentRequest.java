package com.java.erp.modules.gradebook.dto.request;

import com.java.erp.modules.gradebook.entity.Assessment;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Full replacement of a DRAFT assessment's details. The subject offering cannot change. */
public record UpdateAssessmentRequest(
        @NotBlank @Size(max = 150) String title,
        @NotNull Assessment.AssessmentType assessmentType,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal maxMarks,
        @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal passMarks,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal weightage,
        @NotNull LocalDate assessmentDate
) {
}
