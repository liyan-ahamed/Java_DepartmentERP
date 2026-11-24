package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.gradebook.entity.Assessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record AssessmentResponse(
        Long id,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Subject.SubjectType subjectType,
        Long batchId,
        String batchName,
        Long sectionId,
        String sectionName,
        String academicYear,
        Integer semester,
        Long staffId,
        String staffName,
        String title,
        Assessment.AssessmentType assessmentType,
        BigDecimal maxMarks,
        BigDecimal passMarks,
        BigDecimal weightage,
        LocalDate assessmentDate,
        Assessment.Status status,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finalizedAt,
        /** Marks-entry deadline set by the HOD (ISO-8601 with offset), or null. */
        OffsetDateTime marksEntryDueAt
) {
    public static AssessmentResponse from(Assessment a) {
        SubjectOffering o = a.getSubjectOffering();
        Subject s = o.getSubject();
        return new AssessmentResponse(a.getId(), o.getId(), s.getId(), s.getCode(), s.getName(), s.getSubjectType(),
                o.getBatch().getId(), o.getBatch().getBatchName(), o.getSection().getId(), o.getSection().getSectionName(),
                o.getAcademicYear(), o.getSemester(), o.getStaff().getId(), o.getStaff().getName(),
                a.getTitle(), a.getAssessmentType(), a.getMaxMarks(), a.getPassMarks(), a.getWeightage(),
                a.getAssessmentDate(), a.getStatus(), a.getCreatedBy().getId(), a.getCreatedBy().getName(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getFinalizedAt(), a.getMarksEntryDueAt());
    }
}
