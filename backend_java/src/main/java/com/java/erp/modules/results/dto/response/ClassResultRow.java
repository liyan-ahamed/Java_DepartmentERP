package com.java.erp.modules.results.dto.response;

import com.java.erp.modules.results.service.ResultExam;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One student's result in one subject offering for the selected exam (read-only view).
 * source = EXAM_RESULT (END_SEMESTER: examPeriodId set, grade may be set) or ASSESSMENT (CAT_1 / CAT_2: assessmentId
 * and passMarks set, grade null). status: PASS | FAIL | ABSENT | WITHHELD for exam results; GRADED | ABSENT | EXEMPTED
 * | NOT_GRADED for assessments. marks are null unless PASS / FAIL / WITHHELD (with marks) / GRADED.
 * published: exam results = published flag (STAFF only ever receive true); assessments are always FINALIZED, so true.
 * publishedAt: the result's publishedAt, or the assessment's finalizedAt.
 */
public record ClassResultRow(
        ResultExam exam,
        String source,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long batchId,
        String batchName,
        Integer semester,
        String academicYear,
        Long sectionId,
        String sectionName,
        Long studentId,
        String registerNumber,
        String studentName,
        Long assessmentId,
        Long examPeriodId,
        String title,
        BigDecimal marks,
        BigDecimal maxMarks,
        BigDecimal passMarks,
        String grade,
        String status,
        String remarks,
        boolean published,
        LocalDateTime publishedAt
) {
}
