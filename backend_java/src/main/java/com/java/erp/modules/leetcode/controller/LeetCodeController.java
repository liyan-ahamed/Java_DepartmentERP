package com.java.erp.modules.leetcode.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.modules.leetcode.dto.request.UpdateLeetCodeUrlsRequest;
import com.java.erp.modules.leetcode.dto.response.LeetCodeFiltersResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeSectionStatsResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeStudentStatsResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeTopResponse;
import com.java.erp.modules.leetcode.dto.response.LeetCodeTopRefreshResponse;
import com.java.erp.modules.leetcode.service.LeetCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LeetCode progress tracking. HOD and Staff may edit profile URLs for a section;
 * students are read-only. All LeetCode requests are made by the backend.
 */
@RestController
@RequestMapping("/leetcode")
@Tag(name = "LeetCode", description = "Student LeetCode progress tracking")
public class LeetCodeController {

    private final LeetCodeService service;

    public LeetCodeController(LeetCodeService service) {
        this.service = service;
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAnyRole('HOD', 'STAFF')")
    @Operation(summary = "List years and their sections for the LeetCode page")
    public ApiResponse<LeetCodeFiltersResponse> filters() {
        return ApiResponse.success("LeetCode filters fetched successfully", service.getFilters());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('HOD', 'STAFF')")
    @Operation(summary = "Fetch LeetCode statistics for one year and section",
               description = "Queries LeetCode only for students in the selected section that have a profile URL")
    public ApiResponse<LeetCodeSectionStatsResponse> sectionStats(@RequestParam(required = false) String year,
                                                                 @RequestParam(required = false) String section) {
        return ApiResponse.success("LeetCode statistics fetched successfully",
                service.fetchSectionStats(year, section));
    }

    @GetMapping("/top")
    @PreAuthorize("hasAnyRole('HOD', 'STAFF')")
    @Operation(summary = "Top LeetCode solvers of one year, 10 per page",
               description = "Ranks by stored statistics (total, then hard, then medium solved); does not query LeetCode")
    public ApiResponse<LeetCodeTopResponse> topByYear(@RequestParam(required = false) String year,
                                                      @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success("LeetCode top count fetched successfully", service.getTopByYear(year, page));
    }

    @PostMapping("/top/refresh")
    @PreAuthorize("hasAnyRole('HOD', 'STAFF')")
    @Operation(summary = "Refresh the selected year's LeetCode profiles and return the first ranking page")
    public ApiResponse<LeetCodeTopRefreshResponse> refreshTop(@RequestParam(required = false) String year) {
        return ApiResponse.success("LeetCode top count refreshed", service.refreshTopByYear(year));
    }

    @PutMapping("/profile-urls")
    @PreAuthorize("hasAnyRole('HOD', 'STAFF')")
    @Operation(summary = "Update LeetCode profile URLs for students of one year and section",
               description = "Only changed URLs are saved; a changed URL clears the old statistics until the next fetch")
    public ApiResponse<Integer> updateProfileUrls(@Valid @RequestBody UpdateLeetCodeUrlsRequest request) {
        return ApiResponse.success("LeetCode profile URLs updated successfully", service.updateProfileUrls(request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get the logged-in student's stored LeetCode profile and statistics")
    public ApiResponse<LeetCodeStudentStatsResponse> myProfile() {
        return ApiResponse.success("LeetCode profile fetched successfully",
                service.getMyProfile(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserEmail()));
    }

    @GetMapping("/me/stats")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Fetch the logged-in student's latest LeetCode statistics")
    public ApiResponse<LeetCodeStudentStatsResponse> myStats() {
        return ApiResponse.success("LeetCode statistics fetched successfully",
                service.fetchMyStats(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserEmail()));
    }
}
