package com.java.erp.modules.portfolio.dto.response;

import com.java.erp.modules.portfolio.entity.PortfolioEntry;

import java.util.Map;

/** Counts over all of the student's own entries; byActivityType includes every status. */
public record MyPortfolioSummaryResponse(
        long totalEntries,
        long draft,
        long submitted,
        long verified,
        long rejected,
        Map<PortfolioEntry.ActivityType, Long> byActivityType
) {
}
