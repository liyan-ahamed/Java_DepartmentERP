package com.java.erp.modules.timetable;

import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule;
import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule.Period;
import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule.PeriodType;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionPeriodScheduleTest {

    private static LocalTime t(String value) {
        return LocalTime.parse(value);
    }

    @Test
    void eightTeachingPeriodsWithOfficialTimes() {
        List<String> expected = List.of("08:15-09:05", "09:05-09:55", "10:10-11:00", "11:00-11:50",
                "11:50-12:40", "13:30-14:15", "14:15-15:00", "15:00-15:45");
        assertThat(InstitutionPeriodSchedule.TEACHING_PERIODS).hasSize(8);
        for (int i = 0; i < 8; i++) {
            Period p = InstitutionPeriodSchedule.TEACHING_PERIODS.get(i);
            assertThat(p.periodNumber()).isEqualTo(i + 1);
            assertThat(p.label()).isEqualTo("Period " + (i + 1));
            assertThat(p.type()).isEqualTo(PeriodType.TEACHING);
            assertThat(p.startTime() + "-" + p.endTime()).isEqualTo(expected.get(i));
            assertThat(InstitutionPeriodSchedule.teachingPeriod(i + 1)).contains(p);
        }
    }

    @Test
    void breakAndLunchAreNonTeachingWithoutPeriodNumber() {
        List<Period> nonTeaching = InstitutionPeriodSchedule.DAY.stream().filter(p -> !p.isTeaching()).toList();
        assertThat(nonTeaching).extracting(Period::type).containsExactly(PeriodType.BREAK, PeriodType.LUNCH);
        assertThat(nonTeaching).allSatisfy(p -> assertThat(p.periodNumber()).isNull());
        assertThat(nonTeaching.get(0).startTime()).isEqualTo(t("09:55"));
        assertThat(nonTeaching.get(0).endTime()).isEqualTo(t("10:10"));
        assertThat(nonTeaching.get(1).startTime()).isEqualTo(t("12:40"));
        assertThat(nonTeaching.get(1).endTime()).isEqualTo(t("13:30"));
    }

    @Test
    void dayIsContiguousAndInTimeOrder() {
        List<Period> day = InstitutionPeriodSchedule.DAY;
        assertThat(day).hasSize(10);
        for (int i = 1; i < day.size(); i++) {
            assertThat(day.get(i).startTime()).isEqualTo(day.get(i - 1).endTime());
        }
    }

    @Test
    void breakAndLunchTimesAreDetectedAndInvalidPeriodsRejected() {
        assertThat(InstitutionPeriodSchedule.overlapsNonTeaching(t("09:55"), t("10:10"))).isTrue();
        assertThat(InstitutionPeriodSchedule.overlapsNonTeaching(t("12:40"), t("13:30"))).isTrue();
        assertThat(InstitutionPeriodSchedule.overlapsNonTeaching(t("09:30"), t("10:20"))).isTrue();
        assertThat(InstitutionPeriodSchedule.overlapsNonTeaching(t("09:05"), t("09:55"))).isFalse();
        assertThat(InstitutionPeriodSchedule.teachingPeriod(0)).isEmpty();
        assertThat(InstitutionPeriodSchedule.teachingPeriod(9)).isEmpty();
        assertThat(InstitutionPeriodSchedule.teachingPeriod(null)).isEmpty();
        assertThat(InstitutionPeriodSchedule.teachingPeriodAt(t("09:00"), t("09:50"))).isEmpty();
        assertThat(InstitutionPeriodSchedule.teachingPeriodAt(t("13:30"), t("14:15"))).map(Period::periodNumber).contains(6);
    }
}
