package com.java.erp.modules.results.dto.response;

/** Counts of a bulk save (created/updated/unchanged) or of a publish (published). */
public record ResultBatchResponse(
        Long subjectOfferingId,
        Long examPeriodId,
        int received,
        int created,
        int updated,
        int unchanged,
        int published
) {
}
