package com.java.erp.modules.attendance;

import com.java.erp.modules.attendance.dto.response.AttendanceStats;
import com.java.erp.modules.attendance.service.AttendancePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendancePolicyTest {

    private final AttendancePolicy policy = new AttendancePolicy(new BigDecimal("75"));

    @Test
    void presentCountsAsAttended() {
        AttendanceStats s = policy.compute(4, 0, 0, 0);
        assertThat(s.attendancePercentage()).isEqualByComparingTo("100");
        assertThat(s.belowThreshold()).isFalse();
        assertThat(s.shortagePercentagePoints()).isEqualByComparingTo("0");
    }

    @Test
    void absentCountsAgainst() {
        AttendanceStats s = policy.compute(1, 2, 0, 0);
        assertThat(s.attendancePercentage()).isEqualByComparingTo("33.33");
        assertThat(s.eligible()).isEqualTo(3);
        assertThat(s.belowThreshold()).isTrue();
        assertThat(s.shortagePercentagePoints()).isEqualByComparingTo("41.67");
    }

    @Test
    void onDutyCountsAsAttended() {
        AttendanceStats s = policy.compute(2, 1, 1, 0);
        assertThat(s.attended()).isEqualTo(3);
        assertThat(s.attendancePercentage()).isEqualByComparingTo("75.00");
        assertThat(s.belowThreshold()).isFalse(); // exactly at the minimum
    }

    @Test
    void excusedIsLeftOutOfTheDenominator() {
        AttendanceStats s = policy.compute(3, 1, 0, 4);
        assertThat(s.totalSessions()).isEqualTo(8);
        assertThat(s.eligible()).isEqualTo(4);
        assertThat(s.attendancePercentage()).isEqualByComparingTo("75.00");
    }

    @Test
    void zeroDenominatorGivesNullPercentage() {
        AttendanceStats none = policy.compute(0, 0, 0, 0);
        AttendanceStats onlyExcused = policy.compute(0, 0, 0, 3);
        assertThat(none.attendancePercentage()).isNull();
        assertThat(onlyExcused.attendancePercentage()).isNull();
        assertThat(onlyExcused.belowThreshold()).isFalse();
        assertThat(onlyExcused.shortagePercentagePoints()).isNull();
    }

    @Test
    void thresholdComesFromConfiguration() {
        AttendancePolicy strict = new AttendancePolicy(new BigDecimal("80"));
        assertThat(strict.compute(3, 1, 0, 0).belowThreshold()).isTrue();
        assertThat(policy.compute(3, 1, 0, 0).belowThreshold()).isFalse();
        assertThatThrownBy(() -> new AttendancePolicy(new BigDecimal("101"))).isInstanceOf(IllegalStateException.class);
    }
}
