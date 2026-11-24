package com.java.erp.modules.leetcode.dto.response;

import java.time.LocalDateTime;

/**
 * One student's LeetCode profile and statistics.
 *
 * status:
 *  - SYNCED           statistics were fetched from LeetCode in this request
 *  - NOT_SYNCED       stored statistics only (no LeetCode request was made)
 *  - FAILED           the LeetCode lookup failed; stored statistics (if any) are kept and returned
 *  - INVALID_URL      a URL was provided but is not a recognisable LeetCode profile URL
 *  - URL_NOT_PROVIDED no LeetCode URL on record; LeetCode is never queried
 */
public record LeetCodeStudentStatsResponse(
        Long studentId,
        String name,
        String registerNumber,
        String profileUrl,
        String username,
        Integer totalSolved,
        Integer easySolved,
        Integer mediumSolved,
        Integer hardSolved,
        LocalDateTime lastSyncedAt,
        String status,
        String message
) {
}
