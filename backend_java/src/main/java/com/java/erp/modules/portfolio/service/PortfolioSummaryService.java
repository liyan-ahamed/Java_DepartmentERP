package com.java.erp.modules.portfolio.service;

import com.java.erp.modules.portfolio.dto.response.PortfolioSummaryResponse;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.modules.portfolio.repository.PortfolioEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate portfolio figures: the whole department for the HOD, the sections they teach for
 * STAFF. Drafts are never counted; breakdowns count VERIFIED entries.
 */
@Service
public class PortfolioSummaryService {

    private static final PortfolioEntry.Status VERIFIED = PortfolioEntry.Status.VERIFIED;

    private final PortfolioEntryRepository entries;
    private final PortfolioReviewScope reviewScope;

    public PortfolioSummaryService(PortfolioEntryRepository entries, PortfolioReviewScope reviewScope) {
        this.entries = entries;
        this.reviewScope = reviewScope;
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse summary() {
        PortfolioReviewScope.Scope scope = reviewScope.current();
        boolean all = scope.allSections();
        List<Long> ids = scope.queryIds();

        Map<PortfolioEntry.Status, Long> byStatus = new EnumMap<>(PortfolioEntry.Status.class);
        entries.countByStatus(PortfolioEntry.Status.DRAFT, all, ids).forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));

        Map<PortfolioEntry.ActivityType, Long> byType = zeroed(PortfolioEntry.ActivityType.class);
        entries.countByActivityType(VERIFIED, all, ids).forEach(c -> byType.put(c.getActivityType(), c.getTotal()));

        Map<PortfolioEntry.Level, Long> byLevel = zeroed(PortfolioEntry.Level.class);
        entries.countByLevel(VERIFIED, all, ids).forEach(c -> byLevel.put(c.getLevel(), c.getTotal()));

        List<PortfolioSummaryResponse.BatchCount> byBatch = entries.countByBatch(VERIFIED, all, ids).stream()
                .map(c -> new PortfolioSummaryResponse.BatchCount(c.getBatchId(), c.getBatchName(), c.getCurrentYear(), c.getTotal()))
                .toList();
        List<PortfolioSummaryResponse.SectionCount> bySection = entries.countBySection(VERIFIED, all, ids).stream()
                .map(c -> new PortfolioSummaryResponse.SectionCount(c.getSectionId(), c.getSectionName(),
                        c.getBatchId(), c.getBatchName(), c.getTotal()))
                .toList();

        return new PortfolioSummaryResponse(
                all ? PortfolioSummaryResponse.Scope.DEPARTMENT : PortfolioSummaryResponse.Scope.ASSIGNED_SECTIONS,
                byStatus.getOrDefault(VERIFIED, 0L),
                byStatus.getOrDefault(PortfolioEntry.Status.SUBMITTED, 0L),
                byStatus.getOrDefault(PortfolioEntry.Status.REJECTED, 0L),
                entries.countStudents(VERIFIED, all, ids),
                byType, byLevel, byBatch, bySection);
    }

    /** Every enum value with 0, so charts get a stable set of keys. */
    static <E extends Enum<E>> Map<E, Long> zeroed(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            map.put(value, 0L);
        }
        return map;
    }
}
