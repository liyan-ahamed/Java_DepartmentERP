package com.java.erp.modules.gradebook.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Full class list of an assessment, including students who have no grade yet (gradeStatus NOT_GRADED). */
public record GradebookResponse(
        AssessmentResponse assessment,
        int expectedStudentCount,
        List<Row> students
) {
    public record Row(
            Long studentId,
            String registerNumber,
            String name,
            Long gradeId,
            BigDecimal marks,
            String gradeStatus,
            String remarks,
            LocalDateTime updatedAt
    ) {
    }
}
