package com.java.erp.modules.lessonplan.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** PLANNED lesson plans only. */
public record UpdateLessonPlanRequest(
        @Positive Integer unitNumber,
        @NotBlank @Size(max = 255) String topic,
        @Size(max = 2000) String description,
        @NotNull LocalDate plannedDate,
        @Size(max = 1000) String notes
) {
}
