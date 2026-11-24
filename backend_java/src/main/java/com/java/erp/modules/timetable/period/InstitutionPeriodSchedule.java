package com.java.erp.modules.timetable.period;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * The institution-wide daily period structure. This class is the ONLY place the period times live:
 * timetable validation, the {@code GET /timetable/periods} endpoint and the dev seeders all read it.
 *
 * Teaching periods are numbered 1-8. Break and lunch are non-teaching and carry no period number.
 */
public final class InstitutionPeriodSchedule {

    public enum PeriodType { TEACHING, BREAK, LUNCH }

    /** One row of the daily schedule. periodNumber is null for BREAK and LUNCH. */
    public record Period(Integer periodNumber, String label, LocalTime startTime, LocalTime endTime, PeriodType type) {
        public boolean isTeaching() {
            return type == PeriodType.TEACHING;
        }
    }

    public static final int FIRST_PERIOD = 1;
    public static final int LAST_PERIOD = 8;

    /** The full day in time order (8 teaching periods, one break, one lunch). */
    public static final List<Period> DAY = List.of(
            teaching(1, "08:15", "09:05"),
            teaching(2, "09:05", "09:55"),
            new Period(null, "Break", time("09:55"), time("10:10"), PeriodType.BREAK),
            teaching(3, "10:10", "11:00"),
            teaching(4, "11:00", "11:50"),
            teaching(5, "11:50", "12:40"),
            new Period(null, "Lunch", time("12:40"), time("13:30"), PeriodType.LUNCH),
            teaching(6, "13:30", "14:15"),
            teaching(7, "14:15", "15:00"),
            teaching(8, "15:00", "15:45"));

    public static final List<Period> TEACHING_PERIODS = DAY.stream().filter(Period::isTeaching).toList();

    private InstitutionPeriodSchedule() {
    }

    /** The teaching period with this number, or empty for anything outside 1-8. */
    public static Optional<Period> teachingPeriod(Integer periodNumber) {
        if (periodNumber == null || periodNumber < FIRST_PERIOD || periodNumber > LAST_PERIOD) {
            return Optional.empty();
        }
        return Optional.of(TEACHING_PERIODS.get(periodNumber - FIRST_PERIOD));
    }

    /** The teaching period that starts and ends exactly at these times, if any. */
    public static Optional<Period> teachingPeriodAt(LocalTime start, LocalTime end) {
        return TEACHING_PERIODS.stream()
                .filter(p -> p.startTime().equals(start) && p.endTime().equals(end))
                .findFirst();
    }

    /** True when [start, end) overlaps the break or lunch. */
    public static boolean overlapsNonTeaching(LocalTime start, LocalTime end) {
        return DAY.stream().filter(p -> !p.isTeaching())
                .anyMatch(p -> start.isBefore(p.endTime()) && end.isAfter(p.startTime()));
    }

    private static Period teaching(int number, String start, String end) {
        return new Period(number, "Period " + number, time(start), time(end), PeriodType.TEACHING);
    }

    private static LocalTime time(String value) {
        return LocalTime.parse(value);
    }
}
