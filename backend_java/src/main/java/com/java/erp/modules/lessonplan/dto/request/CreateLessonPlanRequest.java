package com.java.erp.modules.lessonplan.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateLessonPlanRequest(
        @NotNull Long subjectOfferingId,
        @Positive Integer unitNumber,
        @NotBlank @Size(max = 255) String topic,
        @Size(max = 2000) String description,
        @NotNull LocalDate plannedDate,
        @Size(max = 1000) String notes
) {
}
