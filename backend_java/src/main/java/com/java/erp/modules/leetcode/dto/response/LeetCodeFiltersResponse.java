package com.java.erp.modules.leetcode.dto.response;

import java.util.List;

/**
 * Year and section options for the LeetCode page, read from the existing batches/sections.
 */
public record LeetCodeFiltersResponse(List<YearOption> years) {
    public record YearOption(String value, String label, List<String> sections) {}
}
