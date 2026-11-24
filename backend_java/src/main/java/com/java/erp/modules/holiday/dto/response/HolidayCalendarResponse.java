package com.java.erp.modules.holiday.dto.response;

import java.time.LocalDateTime;

/**
 * Publication state of one calendar year. status: NOT_CREATED | DRAFT | PUBLISHED for the HOD;
 * STAFF / STUDENT see NOT_PUBLISHED instead of NOT_CREATED / DRAFT, with holidayCount null.
 */
public record HolidayCalendarResponse(
        int year,
        String status,
        LocalDateTime publishedAt,
        String publishedBy,
        Long holidayCount
) {
    public static final String NOT_CREATED = "NOT_CREATED";
    public static final String NOT_PUBLISHED = "NOT_PUBLISHED";
}
