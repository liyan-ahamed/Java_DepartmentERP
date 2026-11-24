package com.java.erp.modules.holiday.dto.response;

import java.util.List;

/**
 * Outcome of one holiday sync over every source (top-level counts are the sums).
 * status: SOURCE_UNAVAILABLE if any source failed, else SYNCED if any source had data, else NO_DATA.
 * A source that failed or had no data leaves its cached holidays untouched. SKIPPED (per source only): the college
 * calendar file was ignored because that year's calendar already exists and is managed in the ERP.
 */
public record HolidaySyncResponse(
        int year,
        String source,
        Status status,
        int inserted,
        int updated,
        int unchanged,
        int removed,
        String message,
        List<SourceResult> sources
) {
    public enum Status { SYNCED, NO_DATA, SOURCE_UNAVAILABLE, SKIPPED }

    public record SourceResult(String source, Status status, int inserted, int updated, int unchanged, int removed,
                               String message) {
    }
}
