package com.java.erp.modules.academic.dto.request;

import com.java.erp.modules.academic.entity.Subject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or fully replace a subject. Code is stored upper-case; active defaults to true.
 * subjectType null = not yet classified. credits: whole number, 0 allowed, null = not recorded.
 */
public record SubjectRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9_-]*\\s*$", message = "may contain only letters, digits, '-' and '_'")
        String code,
        @NotBlank @Size(max = 150) String name,
        Subject.SubjectType subjectType,
        @Min(0) @Max(40) Integer credits,
        Boolean active
) {
}
