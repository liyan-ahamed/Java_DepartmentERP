package com.java.erp.modules.academic.dto.request;

import jakarta.validation.constraints.*;

/** Create or fully replace a subject offering. active defaults to true. */
public record SubjectOfferingRequest(
        @NotNull Long subjectId,
        @NotNull Long batchId,
        @NotNull Long sectionId,
        @NotNull Long staffId,
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "must look like 2026-2027") String academicYear,
        @NotNull @Min(1) @Max(8) Integer semester,
        Boolean active
) {
}
