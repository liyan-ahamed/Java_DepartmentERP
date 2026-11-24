package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.academic.dto.response.SubjectOfferingResponse;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentInternalMark;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Internal-assessment view of one subject offering.
 *
 * Totals are only ever read from student_internal_marks; nothing is calculated here. Until the
 * formulas are configured every total is null and calculationStatus is PENDING_CALCULATION_CONFIGURATION.
 * practicalComponentApplicable is false for THEORY subjects (practicalComponentTotal is always null).
 * finalizedAssessmentsByType lists the offering's FINALIZED assessments grouped by type, with the
 * student's raw result for each.
 */
public record InternalMarksResponse(
        SubjectOfferingResponse offering,
        boolean practicalComponentApplicable,
        List<StudentEntry> students
) {
    public record StudentEntry(
            Long studentId,
            String registerNumber,
            String name,
            BigDecimal theoryComponentTotal,
            BigDecimal practicalComponentTotal,
            BigDecimal totalInternalMarks,
            StudentInternalMark.CalculationStatus calculationStatus,
            String calculationVersion,
            LocalDateTime calculatedAt,
            Map<Assessment.AssessmentType, List<AssessmentResult>> finalizedAssessmentsByType
    ) {
    }

    public record AssessmentResult(
            Long assessmentId,
            String title,
            LocalDate assessmentDate,
            BigDecimal maxMarks,
            BigDecimal marks,
            String gradeStatus
    ) {
    }
}
