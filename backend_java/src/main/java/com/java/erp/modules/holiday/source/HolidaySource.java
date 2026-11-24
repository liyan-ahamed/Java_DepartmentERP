package com.java.erp.modules.holiday.source;

import com.java.erp.modules.holiday.entity.Holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where holiday data comes from. The sync reads a source and caches the result in the holidays table,
 * so pages never call the source directly.
 */
public interface HolidaySource {

    /** One holiday as published by the source. reference = the official document it was taken from. */
    record HolidayRecord(LocalDate date, String name, Holiday.Scope scope, String reference) {
    }

    /** Thrown when the source exists but cannot be read right now (the cached holidays are kept). */
    class HolidaySourceUnavailableException extends RuntimeException {
        public HolidaySourceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Short name for logs and API responses. */
    String name();

    /**
     * The scopes this source owns. The sync only writes (and only ever removes) rows of these scopes for this
     * source, so e.g. the government-list sync can never touch the college calendar.
     */
    Set<Holiday.Scope> scopes();

    /**
     * All holidays of the year (every scope), or empty when the source has no data for that year yet.
     *
     * @throws HolidaySourceUnavailableException when the data exists but cannot be read or is invalid
     */
    Optional<List<HolidayRecord>> fetch(int year);
}
