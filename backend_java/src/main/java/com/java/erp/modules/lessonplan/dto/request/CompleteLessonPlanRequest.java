package com.java.erp.modules.lessonplan.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** completedDate defaults to today and cannot be in the future; notes (optional) replace the current notes. */
public record CompleteLessonPlanRequest(
        LocalDate completedDate,
        @Size(max = 1000) String notes
) {
}
