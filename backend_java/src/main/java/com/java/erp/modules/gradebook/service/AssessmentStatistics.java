package com.java.erp.modules.gradebook.service;

import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.entity.StudentGrade.GradeStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Statistics of one assessment over its expected class list (the only implementation; used by the summary and
 * the overview). Numeric figures use GRADED marks only: ABSENT and EXEMPTED students are counted separately and
 * never as zero. average/highest/lowest are null when nothing is graded; pass figures are null without pass marks
 * (passPercentage = passCount / gradedCount * 100, 2 dp). Grade rows of students outside the class list are ignored.
 */
public record AssessmentStatistics(
        int expectedStudentCount,
        int gradedCount,
        int absentCount,
        int exemptedCount,
        int notGradedCount,
        BigDecimal average,
        BigDecimal highest,
        BigDecimal lowest,
        Integer passCount,
        Integer failCount,
        BigDecimal passPercentage
) {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static AssessmentStatistics calculate(Collection<Long> expectedStudentIds,
                                                 Map<Long, StudentGrade> gradesByStudent, BigDecimal passMarks) {
        List<BigDecimal> marks = new ArrayList<>();
        int absent = 0;
        int exempted = 0;
        int notGraded = 0;
        for (Long studentId : expectedStudentIds) {
            StudentGrade g = gradesByStudent.get(studentId);
            if (g == null) notGraded++;
            else if (g.getGradeStatus() == GradeStatus.GRADED) marks.add(g.getMarks());
            else if (g.getGradeStatus() == GradeStatus.ABSENT) absent++;
            else exempted++;
        }

        BigDecimal average = null, highest = null, lowest = null;
        if (!marks.isEmpty()) {
            BigDecimal sum = marks.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            average = sum.divide(BigDecimal.valueOf(marks.size()), 2, RoundingMode.HALF_UP);
            highest = marks.stream().max(Comparator.naturalOrder()).orElseThrow();
            lowest = marks.stream().min(Comparator.naturalOrder()).orElseThrow();
        }
        Integer passCount = null, failCount = null;
        BigDecimal passPercentage = null;
        if (passMarks != null) {
            passCount = (int) marks.stream().filter(m -> m.compareTo(passMarks) >= 0).count();
            failCount = marks.size() - passCount;
            if (!marks.isEmpty()) {
                passPercentage = BigDecimal.valueOf(passCount).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(marks.size()), 2, RoundingMode.HALF_UP);
            }
        }
        return new AssessmentStatistics(expectedStudentIds.size(), marks.size(), absent, exempted, notGraded,
                average, highest, lowest, passCount, failCount, passPercentage);
    }

    /** Every expected student has a mark or an explicit ABSENT / EXEMPTED status. */
    public boolean complete() {
        return notGradedCount == 0;
    }
}
