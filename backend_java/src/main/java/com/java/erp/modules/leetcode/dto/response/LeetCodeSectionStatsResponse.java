package com.java.erp.modules.leetcode.dto.response;

import java.util.List;

/**
 * LeetCode statistics for every active student in one year/section.
 */
public record LeetCodeSectionStatsResponse(
        String year,
        String section,
        int totalStudents,
        int profilesQueried,
        int synced,
        int failed,
        int urlNotProvided,
        List<LeetCodeStudentStatsResponse> students
) {
}
