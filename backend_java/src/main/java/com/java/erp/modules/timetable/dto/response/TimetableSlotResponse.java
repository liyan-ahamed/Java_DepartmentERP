package com.java.erp.modules.timetable.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.timetable.entity.TimetableSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One recurring weekly slot. date / isHoliday / holidayName / holidayScope are filled only when a week
 * is asked for (weekOf): date = the slot's day in that week; the holiday fields describe the published college
 * holidays of that date that apply to the slot's cohort (holidayScope is then COLLEGE). The recurring slot itself
 * is never changed by a holiday.
 */
public record TimetableSlotResponse(
        Long id,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long batchId,
        String batchName,
        Long sectionId,
        String sectionName,
        Long staffId,
        String staffName,
        String academicYear,
        Integer semester,
        DayOfWeek dayOfWeek,
        Integer periodNumber,
        LocalTime startTime,
        LocalTime endTime,
        String room,
        TimetableSlot.SlotType slotType,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDate date,
        Boolean isHoliday,
        String holidayName,
        Holiday.Scope holidayScope
) {
    public static TimetableSlotResponse from(TimetableSlot t) {
        return of(t, null, null);
    }

    /** The slot on a concrete date; holidays = the holidays of that date (may be empty). */
    public static TimetableSlotResponse onDate(TimetableSlot t, LocalDate date, List<HolidayResponse> holidays) {
        return of(t, date, holidays == null ? List.of() : holidays);
    }

    private static TimetableSlotResponse of(TimetableSlot t, LocalDate date, List<HolidayResponse> holidays) {
        SubjectOffering o = t.getSubjectOffering();
        Boolean isHoliday = holidays == null ? null : !holidays.isEmpty();
        String holidayName = holidays == null || holidays.isEmpty() ? null
                : holidays.stream().map(HolidayResponse::name).distinct().collect(Collectors.joining(" / "));
        Holiday.Scope holidayScope = holidays == null || holidays.isEmpty() ? null : holidays.get(0).scope();
        return new TimetableSlotResponse(t.getId(), o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                o.getSubject().getName(), o.getBatch().getId(), o.getBatch().getBatchName(), o.getSection().getId(),
                o.getSection().getSectionName(), o.getStaff().getId(), o.getStaff().getName(), o.getAcademicYear(),
                o.getSemester(), t.getDayOfWeek(), t.getPeriodNumber(), t.getStartTime(), t.getEndTime(), t.getRoom(),
                t.getSlotType(), t.isActive(), t.getCreatedAt(), t.getUpdatedAt(), date, isHoliday, holidayName, holidayScope);
    }
}
