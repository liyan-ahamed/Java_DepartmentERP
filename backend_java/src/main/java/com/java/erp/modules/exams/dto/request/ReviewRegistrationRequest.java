package com.java.erp.modules.exams.dto.request;

import jakarta.validation.constraints.Size;

/** Optional on approval; required (5+ characters) on rejection. */
public record ReviewRegistrationRequest(
        @Size(max = 500) String reviewNote
) {
}
