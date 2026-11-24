package com.java.erp.modules.gradebook.dto.response;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.service.AssessmentStatistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One subject offering ("class") of the assessment overview with all its assessments (DRAFT and FINALIZED) and
 * each assessment's statistics over the gradebook class list (see {@link AssessmentStatistics}).
 * Offerings without assessments are included with an empty list.
 */
public record AssessmentOverviewResponse(
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
        boolean offeringActive,
        Long staffId,
        String staffName,
        int classSize,
        int draftCount,
        int finalizedCount,
        List<Item> assessments
) {
    /**
     * readyToFinalize: DRAFT and every student has a mark or an explicit ABSENT / EXEMPTED status
     * (statistics.notGradedCount == 0). The finalize endpoint re-checks this server-side.
     */
    public record Item(
            Long id,
            String title,
            Assessment.AssessmentType assessmentType,
            BigDecimal maxMarks,
            BigDecimal passMarks,
            BigDecimal weightage,
            LocalDate assessmentDate,
            Assessment.Status status,
            LocalDateTime finalizedAt,
            OffsetDateTime marksEntryDueAt,
            boolean readyToFinalize,
            AssessmentStatistics statistics
    ) {
    }
}
