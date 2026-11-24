package com.java.erp.modules.calendar.dto;

import com.java.erp.modules.calendar.entity.CalendarEntry;
import com.java.erp.modules.holiday.entity.Holiday;
import java.time.LocalDate;

public record CalendarItemResponse(String id, String title, LocalDate startDate, LocalDate endDate,
                                   boolean holiday, boolean government, String source, String createdByName) {
    public static CalendarItemResponse from(CalendarEntry entry) {
        return new CalendarItemResponse("entry-" + entry.getId(), entry.getTitle(), entry.getEventDate(),
                entry.getEventDate(), entry.isHoliday(), false, "Department", entry.getCreatedBy().getName());
    }
    public static CalendarItemResponse from(Holiday holiday) {
        return new CalendarItemResponse("government-" + holiday.getId(), holiday.getName(), holiday.getHolidayDate(),
                holiday.getLastDate(), true, true, holiday.getSource(), null);
    }
}
