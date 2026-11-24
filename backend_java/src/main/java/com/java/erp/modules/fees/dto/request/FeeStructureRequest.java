package com.java.erp.modules.fees.dto.request;

import com.java.erp.modules.fees.entity.FeeStructure;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * amount: up to 10 digits and 2 decimals, > 0. active defaults to true.
 * Either academicYear (a term charge) or studentYear 1-4 (a charge of the "Year N" template, no academic year).
 */
public record FeeStructureRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull FeeStructure.FeeType feeType,
        @Min(1) @Max(4) Integer studentYear,
        @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "must look like 2026-2027") String academicYear,
        @Min(1) @Max(8) Integer semester,
        Long batchId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        LocalDate dueDate,
        Boolean active
) {
}
