package com.java.erp.modules.exams.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The full set of subject offerings to register for (replaces the previous selection). */
public record SaveRegistrationRequest(
        @NotEmpty @Size(max = 20) List<@NotNull Long> subjectOfferingIds
) {
}
