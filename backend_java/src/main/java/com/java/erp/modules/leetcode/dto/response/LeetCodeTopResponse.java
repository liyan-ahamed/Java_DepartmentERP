package com.java.erp.modules.leetcode.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One page (10 students) of a year's LeetCode ranking, built from stored statistics.
 */
public record LeetCodeTopResponse(
        String year,
        int page,
        long totalRanked,
        boolean hasMore,
        List<RankedStudent> students
) {
    public record RankedStudent(
            int rank,
            Long studentId,
            String name,
            String section,
            Integer totalSolved,
            Integer easySolved,
            Integer mediumSolved,
            Integer hardSolved,
            LocalDateTime lastSyncedAt
    ) {}
}
