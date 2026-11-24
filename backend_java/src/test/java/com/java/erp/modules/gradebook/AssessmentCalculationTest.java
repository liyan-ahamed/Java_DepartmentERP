package com.java.erp.modules.gradebook;

import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.entity.StudentGrade.GradeStatus;
import com.java.erp.modules.gradebook.service.AssessmentStatistics;
import com.java.erp.modules.gradebook.service.GradeRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure calculation and validation rules (no Spring, no database). */
class AssessmentCalculationTest {

    private static StudentGrade grade(GradeStatus status, String marks) {
        StudentGrade g = new StudentGrade();
        g.setGradeStatus(status);
        g.setMarks(marks == null ? null : new BigDecimal(marks));
        return g;
    }

    @Test
    void statisticsUseGradedMarksOnlyAndCountAbsentSeparately() {
        Map<Long, StudentGrade> grades = Map.of(
                1L, grade(GradeStatus.GRADED, "40"),
                2L, grade(GradeStatus.GRADED, "20.5"),
                3L, grade(GradeStatus.ABSENT, null),
                4L, grade(GradeStatus.EXEMPTED, null),
                99L, grade(GradeStatus.GRADED, "1")); // not in the class list: ignored
        AssessmentStatistics s = AssessmentStatistics.calculate(List.of(1L, 2L, 3L, 4L, 5L), grades, new BigDecimal("25"));

        assertThat(s.expectedStudentCount()).isEqualTo(5);
        assertThat(s.gradedCount()).isEqualTo(2);
        assertThat(s.absentCount()).isEqualTo(1);
        assertThat(s.exemptedCount()).isEqualTo(1);
        assertThat(s.notGradedCount()).isEqualTo(1);
        assertThat(s.complete()).isFalse();
        assertThat(s.average()).isEqualByComparingTo("30.25");
        assertThat(s.highest()).isEqualByComparingTo("40");
        assertThat(s.lowest()).isEqualByComparingTo("20.5");
        assertThat(s.passCount()).isEqualTo(1);
        assertThat(s.failCount()).isEqualTo(1);
        assertThat(s.passPercentage()).isEqualByComparingTo("50.00");
    }

    @Test
    void statisticsWithoutMarksOrPassMarksAreNull() {
        AssessmentStatistics s = AssessmentStatistics.calculate(List.of(1L), Map.of(1L, grade(GradeStatus.ABSENT, null)), null);
        assertThat(s.average()).isNull();
        assertThat(s.highest()).isNull();
        assertThat(s.passCount()).isNull();
        assertThat(s.passPercentage()).isNull();
        assertThat(s.complete()).isTrue();
    }

    @Test
    void markRules() {
        BigDecimal max = new BigDecimal("50");
        assertThat(GradeRules.problem(GradeStatus.GRADED, new BigDecimal("50"), max)).isEmpty();
        assertThat(GradeRules.problem(GradeStatus.GRADED, new BigDecimal("0"), max)).isEmpty();
        assertThat(GradeRules.problem(GradeStatus.GRADED, new BigDecimal("12.50"), max)).isEmpty();
        assertThat(GradeRules.problem(GradeStatus.GRADED, new BigDecimal("50.01"), max)).isPresent();
        assertThat(GradeRules.problem(GradeStatus.GRADED, new BigDecimal("-1"), max)).isPresent();
        assertThat(GradeRules.problem(GradeStatus.GRADED, new BigDecimal("12.345"), max)).isPresent();
        assertThat(GradeRules.problem(GradeStatus.GRADED, null, max)).isPresent();
        assertThat(GradeRules.problem(GradeStatus.ABSENT, null, max)).isEmpty();
        assertThat(GradeRules.problem(GradeStatus.ABSENT, BigDecimal.ZERO, max)).isPresent();
        assertThat(GradeRules.problem(GradeStatus.EXEMPTED, null, max)).isEmpty();
    }
}
