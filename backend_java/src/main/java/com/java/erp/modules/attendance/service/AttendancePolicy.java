package com.java.erp.modules.attendance.service;

import com.java.erp.modules.attendance.dto.response.AttendanceStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single place where attendance percentages are calculated (FINALIZED sessions only):
 *   attended = PRESENT + ON_DUTY
 *   eligible = PRESENT + ABSENT + ON_DUTY       (EXCUSED is left out of the denominator)
 *   percentage = attended / eligible * 100 (2 decimals, half-up), or null when eligible = 0
 * The minimum comes from attendance.minimum-percentage (default 75).
 */
@Component
public class AttendancePolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal minimumPercentage;

    public AttendancePolicy(@Value("${attendance.minimum-percentage:75}") BigDecimal minimumPercentage) {
        if (minimumPercentage.signum() < 0 || minimumPercentage.compareTo(HUNDRED) > 0) {
            throw new IllegalStateException("attendance.minimum-percentage must be between 0 and 100");
        }
        this.minimumPercentage = minimumPercentage;
    }

    public BigDecimal minimumPercentage() {
        return minimumPercentage;
    }

    public AttendanceStats compute(long present, long absent, long onDuty, long excused) {
        long attended = present + onDuty;
        long eligible = present + absent + onDuty;
        BigDecimal percentage = eligible == 0 ? null
                : BigDecimal.valueOf(attended).multiply(HUNDRED).divide(BigDecimal.valueOf(eligible), 2, RoundingMode.HALF_UP);
        boolean below = percentage != null && percentage.compareTo(minimumPercentage) < 0;
        BigDecimal shortage = percentage == null ? null
                : below ? minimumPercentage.subtract(percentage).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new AttendanceStats(present + absent + onDuty + excused, present, absent, onDuty, excused, attended,
                eligible, percentage, minimumPercentage, below, shortage);
    }
}
