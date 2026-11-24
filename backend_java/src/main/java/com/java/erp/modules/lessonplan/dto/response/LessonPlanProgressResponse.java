package com.java.erp.modules.lessonplan.dto.response;

import java.math.BigDecimal;

/**
 * totalPlanned = PLANNED + COMPLETED (cancelled topics no longer count);
 * remaining = PLANNED; completionPercentage = completed / totalPlanned * 100, null when nothing is planned.
 */
public record LessonPlanProgressResponse(
        Long subjectOfferingId,
        String subjectCode,
        String subjectName,
        long totalPlanned,
        long completed,
        long remaining,
        long cancelled,
        BigDecimal completionPercentage
) {
}
