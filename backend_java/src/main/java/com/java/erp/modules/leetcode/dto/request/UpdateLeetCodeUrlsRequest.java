package com.java.erp.modules.leetcode.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * New LeetCode profile URLs for students of one year/section. Only the students
 * listed are touched; a blank URL clears the student's profile.
 */
public record UpdateLeetCodeUrlsRequest(
        String year,
        String section,
        @NotNull @Valid List<StudentUrl> students
) {
    public record StudentUrl(
            @NotNull Long studentId,
            @Size(max = 255) String profileUrl
    ) {}
}
