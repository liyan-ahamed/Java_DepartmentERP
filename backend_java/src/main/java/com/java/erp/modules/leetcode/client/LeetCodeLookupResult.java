package com.java.erp.modules.leetcode.client;

/**
 * Outcome of a single LeetCode profile lookup.
 */
public record LeetCodeLookupResult(boolean success, int totalSolved, int easySolved,
                                   int mediumSolved, int hardSolved, String error) {

    public static LeetCodeLookupResult success(int total, int easy, int medium, int hard) {
        return new LeetCodeLookupResult(true, total, easy, medium, hard, null);
    }

    public static LeetCodeLookupResult failure(String error) {
        return new LeetCodeLookupResult(false, 0, 0, 0, 0, error);
    }
}
