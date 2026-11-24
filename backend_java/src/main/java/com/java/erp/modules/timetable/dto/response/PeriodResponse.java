package com.java.erp.modules.timetable.dto.response;

import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule;

import java.time.LocalTime;

/** One row of the official daily schedule. periodNumber is null for BREAK and LUNCH. */
public record PeriodResponse(
        Integer periodNumber,
        String label,
        LocalTime startTime,
        LocalTime endTime,
        InstitutionPeriodSchedule.PeriodType type,
        boolean teaching
) {
    public static PeriodResponse from(InstitutionPeriodSchedule.Period p) {
        return new PeriodResponse(p.periodNumber(), p.label(), p.startTime(), p.endTime(), p.type(), p.isTeaching());
    }
}
