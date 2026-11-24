package com.java.erp.modules.portfolio.dto.response;

import com.java.erp.modules.portfolio.entity.PortfolioEntry;

import java.util.List;
import java.util.Map;

/** A student's verified portfolio (VERIFIED entries only, newest activity first). */
public record StudentPortfolioResponse(
        Long studentId,
        String studentName,
        String registerNumber,
        Long batchId,
        String batchName,
        Long sectionId,
        String sectionName,
        long verifiedCount,
        Map<PortfolioEntry.ActivityType, Long> verifiedByActivityType,
        List<PortfolioEntryResponse> entries
) {
}
