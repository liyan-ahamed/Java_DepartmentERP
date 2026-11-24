package com.java.erp.modules.holiday.dto.response;

import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.entity.HolidayCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * category is what the frontend shows; scope/source are provenance. date = startDate (kept for existing clients);
 * endDate is inclusive and null for a one-day holiday. batchIds empty = the whole institution.
 * calendarStatus is the publication state of the year's college calendar (null for government rows).
 */
public record HolidayResponse(
        Long id,
        LocalDate date,
        LocalDate startDate,
        LocalDate endDate,
        String name,
        Holiday.Category category,
        String description,
        List<Long> batchIds,
        Holiday.Scope scope,
        int year,
        HolidayCalendar.Status calendarStatus,
        String source,
        LocalDateTime lastSyncedAt,
        LocalDateTime updatedAt
) {
    public static HolidayResponse from(Holiday h, HolidayCalendar.Status calendarStatus) {
        return new HolidayResponse(h.getId(), h.getHolidayDate(), h.getHolidayDate(), h.getEndDate(), h.getName(),
                h.getCategory(), h.getDescription(), h.getBatchIds().stream().sorted().toList(), h.getScope(), h.getYear(),
                h.getScope() == Holiday.Scope.COLLEGE ? calendarStatus : null, h.getSource(), h.getLastSyncedAt(),
                h.getUpdatedAt());
    }

    /** True when the holiday applies to the cohort (institution-wide holidays apply to every cohort). */
    public boolean appliesTo(Long batchId) {
        return batchIds.isEmpty() || batchIds.contains(batchId);
    }
}
