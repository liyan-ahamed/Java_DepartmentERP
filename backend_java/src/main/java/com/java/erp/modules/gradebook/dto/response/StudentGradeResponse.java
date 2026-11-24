package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.gradebook.entity.Assessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One FINALIZED assessment result of the logged-in student, with enough subject data to group by subject. */
public record StudentGradeResponse(
        Long assessmentId,
        String assessmentTitle,
        Assessment.AssessmentType assessmentType,
        LocalDate assessmentDate,
        BigDecimal maxMarks,
        BigDecimal passMarks,
        LocalDateTime publishedAt,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Subject.SubjectType subjectType,
        String academicYear,
        Integer semester,
        BigDecimal marks,
        String gradeStatus,
        String remarks
) {
}
