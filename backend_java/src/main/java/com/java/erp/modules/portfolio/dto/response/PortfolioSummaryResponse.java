package com.java.erp.modules.portfolio.dto.response;

import com.java.erp.modules.portfolio.entity.PortfolioEntry;

import java.util.List;
import java.util.Map;

/**
 * Department (HOD) or assigned-sections (STAFF) portfolio figures. Drafts are private and not
 * counted. The breakdowns count VERIFIED entries only; every enum value is present (0 when none).
 */
public record PortfolioSummaryResponse(
        Scope scope,
        long verified,
        long pendingReview,
        long rejected,
        long studentsWithVerifiedEntries,
        Map<PortfolioEntry.ActivityType, Long> verifiedByActivityType,
        Map<PortfolioEntry.Level, Long> verifiedByLevel,
        List<BatchCount> verifiedByBatch,
        List<SectionCount> verifiedBySection
) {
    public enum Scope { DEPARTMENT, ASSIGNED_SECTIONS }

    public record BatchCount(Long batchId, String batchName, Short currentYear, long verified) {
    }

    public record SectionCount(Long sectionId, String sectionName, Long batchId, String batchName, long verified) {
    }
}
