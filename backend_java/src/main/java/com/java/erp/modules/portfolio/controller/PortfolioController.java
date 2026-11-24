package com.java.erp.modules.portfolio.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.portfolio.dto.request.PortfolioEntryRequest;
import com.java.erp.modules.portfolio.dto.request.RejectEntryRequest;
import com.java.erp.modules.portfolio.dto.request.VerifyEntryRequest;
import com.java.erp.modules.portfolio.dto.response.MyPortfolioSummaryResponse;
import com.java.erp.modules.portfolio.dto.response.PortfolioEntryResponse;
import com.java.erp.modules.portfolio.dto.response.StudentPortfolioResponse;
import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import com.java.erp.modules.portfolio.service.PortfolioReviewService;
import com.java.erp.modules.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Student Achievement & Activity Portfolio.
 * STUDENT: own entries (create, edit/delete while DRAFT or REJECTED, submit).
 * STAFF: review entries of students in sections they currently teach. HOD: review everyone,
 * department summary. Nobody but the student edits an entry's content.
 */
@RestController
@RequestMapping("/portfolio")
@Tag(name = "Portfolio", description = "Student achievements & activities with faculty verification")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioReviewService reviewService;

    public PortfolioController(PortfolioService portfolioService, PortfolioReviewService reviewService) {
        this.portfolioService = portfolioService;
        this.reviewService = reviewService;
    }

    // ---- Student ----

    @PostMapping("/entries")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Create a DRAFT entry in my portfolio", description = "Only activityType and title are needed for a draft.")
    public ResponseEntity<ApiResponse<PortfolioEntryResponse>> create(@Valid @RequestBody PortfolioEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Portfolio entry created successfully", portfolioService.create(request)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My portfolio entries (all statuses)", description = "Filters: status, activityType, year (of startDate).")
    public ApiResponse<PagedResponse<PortfolioEntryResponse>> mine(
            @RequestParam(required = false) PortfolioEntry.Status status,
            @RequestParam(required = false) PortfolioEntry.ActivityType activityType,
            @RequestParam(required = false) @Min(1900) @Max(2100) Integer year,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Portfolio entries fetched successfully",
                portfolioService.mine(status, activityType, year, page, size));
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Counts of my portfolio entries")
    public ApiResponse<MyPortfolioSummaryResponse> mySummary() {
        return ApiResponse.success("Portfolio summary fetched successfully", portfolioService.mySummary());
    }

    @GetMapping("/entries/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','HOD')")
    @Operation(summary = "Get a portfolio entry",
               description = "STUDENT: own entries. STAFF/HOD: non-draft entries of students they may review.")
    public ApiResponse<PortfolioEntryResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Portfolio entry fetched successfully", portfolioService.get(id));
    }

    @PutMapping("/entries/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Edit my DRAFT or REJECTED entry", description = "A REJECTED entry stays REJECTED until it is submitted again.")
    public ApiResponse<PortfolioEntryResponse> update(@PathVariable Long id, @Valid @RequestBody PortfolioEntryRequest request) {
        return ApiResponse.success("Portfolio entry updated successfully", portfolioService.update(id, request));
    }

    @DeleteMapping("/entries/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Delete my DRAFT or REJECTED entry")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        portfolioService.delete(id);
        return ApiResponse.success("Portfolio entry deleted successfully");
    }

    @PostMapping("/entries/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit my entry for verification",
               description = "Allowed from DRAFT or REJECTED. Requires organization, description (20+ chars), level and a past startDate.")
    public ApiResponse<PortfolioEntryResponse> submit(@PathVariable Long id) {
        return ApiResponse.success("Portfolio entry submitted for verification", portfolioService.submit(id));
    }

    // ---- Faculty ----

    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('STAFF','HOD')")
    @Operation(summary = "Review queue",
               description = "status defaults to SUBMITTED (oldest first). STAFF see students of sections they teach; HOD sees all. "
                       + "search matches student name, register number or title.")
    public ApiResponse<PagedResponse<PortfolioEntryResponse>> reviewQueue(
            @RequestParam(required = false) PortfolioEntry.Status status,
            @RequestParam(required = false) PortfolioEntry.ActivityType activityType,
            @RequestParam(required = false) PortfolioEntry.Level level,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Review queue fetched successfully",
                reviewService.queue(status, activityType, level, batchId, sectionId, search, page, size));
    }

    @PostMapping("/entries/{id}/verify")
    @PreAuthorize("hasAnyRole('STAFF','HOD')")
    @Operation(summary = "Verify a SUBMITTED entry", description = "Optional body: { \"reviewNote\": \"...\" }. Notifies the student.")
    public ApiResponse<PortfolioEntryResponse> verify(@PathVariable Long id,
                                                      @Valid @RequestBody(required = false) VerifyEntryRequest request) {
        return ApiResponse.success("Portfolio entry verified",
                reviewService.verify(id, request == null ? null : request.reviewNote()));
    }

    @PostMapping("/entries/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF','HOD')")
    @Operation(summary = "Reject a SUBMITTED entry", description = "reviewNote (10+ chars) tells the student what to change. Notifies the student.")
    public ApiResponse<PortfolioEntryResponse> reject(@PathVariable Long id, @Valid @RequestBody RejectEntryRequest request) {
        return ApiResponse.success("Portfolio entry rejected", reviewService.reject(id, request.reviewNote()));
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('STAFF','HOD')")
    @Operation(summary = "A student's verified portfolio", description = "VERIFIED entries only.")
    public ApiResponse<StudentPortfolioResponse> studentPortfolio(@PathVariable Long studentId) {
        return ApiResponse.success("Student portfolio fetched successfully", reviewService.studentPortfolio(studentId));
    }

    // GET /portfolio/summary (analytics) lives in PortfolioAnalyticsController behind features.portfolio-analytics.enabled.
}
