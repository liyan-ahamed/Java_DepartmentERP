package com.java.erp.modules.results.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result entry sheet of one subject offering for one exam period: every enrolled student, with their
 * result or status NOT_ENTERED, plus anyone who already has a result but is no longer enrolled.
 */
public record ResultSheetResponse(
        Long subjectOfferingId,
        String subjectCode,
        String subjectName,
        String sectionName,
        Long examPeriodId,
        String examPeriodName,
        int students,
        int entered,
        int published,
        List<Row> rows
) {
    public record Row(
            Long studentId,
            String registerNumber,
            String studentName,
            boolean enrolled,
            Long resultId,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            String grade,
            String resultStatus,
            String remarks,
            boolean published
    ) {
    }
}
