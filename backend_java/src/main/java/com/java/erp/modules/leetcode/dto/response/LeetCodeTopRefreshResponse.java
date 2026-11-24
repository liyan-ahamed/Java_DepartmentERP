package com.java.erp.modules.leetcode.dto.response;

public record LeetCodeTopRefreshResponse(LeetCodeTopResponse ranking, int refreshedProfiles, int failedProfiles) {}
