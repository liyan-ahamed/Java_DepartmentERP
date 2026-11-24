package com.java.erp.modules.results.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.results.dto.request.BulkResultRequest;
import com.java.erp.modules.results.dto.response.ClassResultRow;
import com.java.erp.modules.results.dto.response.ExamResultResponse;
import com.java.erp.modules.results.dto.response.ResultBatchResponse;
import com.java.erp.modules.results.dto.response.ResultSheetResponse;
import com.java.erp.modules.results.entity.ExamResult;
import com.java.erp.modules.results.service.ClassResultViewService;
import com.java.erp.modules.results.service.ExamResultService;
import com.java.erp.modules.results.service.ResultExam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/results")
@Tag(name = "Results", description = "Formal exam results (staff enter internal results, COE enters end-semester results, "
        + "HOD publishes, students see published)")
public class ExamResultController {

    private final ExamResultService service;
    private final ClassResultViewService resultView;

    public ExamResultController(ExamResultService service, ClassResultViewService resultView) {
        this.service = service;
        this.resultView = resultView;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My published results")
    public ApiResponse<List<ExamResultResponse>> mine(@RequestParam(required = false) Long examPeriodId) {
        return ApiResponse.success("Results fetched successfully", service.myResults(examPeriodId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF','COE')")
    @Operation(summary = "List results", description = "HOD and COE see all; STAFF only results of their own offerings.")
    public ApiResponse<PagedResponse<ExamResultResponse>> list(
            @RequestParam(required = false) Long examPeriodId,
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) ExamResult.ResultStatus resultStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Results fetched successfully",
                service.list(examPeriodId, subjectOfferingId, sectionId, studentId, published, resultStatus, page, size));
    }

    @GetMapping("/subject-offerings/{offeringId}/periods/{periodId}")
    @PreAuthorize("hasAnyRole('HOD','STAFF','COE')")
    @Operation(summary = "Result sheet of a subject offering for an exam period")
    public ApiResponse<ResultSheetResponse> sheet(@PathVariable Long offeringId, @PathVariable Long periodId) {
        return ApiResponse.success("Result sheet fetched successfully", service.sheet(offeringId, periodId));
    }

    @PutMapping("/subject-offerings/{offeringId}/periods/{periodId}")
    @PreAuthorize("hasAnyRole('STAFF','COE')")
    @Operation(summary = "Enter or edit unpublished results (all or nothing)",
               description = "INTERNAL periods: the assigned STAFF member. END_SEMESTER / SUPPLEMENTARY periods: COE only "
                       + "(STAFF get 403, also for their own offerings).")
    public ApiResponse<ResultBatchResponse> save(@PathVariable Long offeringId, @PathVariable Long periodId,
                                                 @Valid @RequestBody BulkResultRequest request) {
        return ApiResponse.success("Results saved successfully", service.saveSheet(offeringId, periodId, request));
    }

    @GetMapping("/by-exam")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Read-only results of one cohort, semester and exam",
               description = "exam = END_SEMESTER (exam results; STAFF see published rows only), CAT_1 or CAT_2 "
                       + "(FINALIZED gradebook assessments of that type; drafts never appear). One row per student and "
                       + "subject offering (and assessment / exam period). STAFF: only offerings assigned to them.")
    public ApiResponse<PagedResponse<ClassResultRow>> byExam(
            @RequestParam Long batchId,
            @RequestParam @Min(1) @Max(8) int semester,
            @RequestParam ResultExam exam,
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long examPeriodId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Results fetched successfully", resultView.byExam(
                batchId, semester, exam, subjectOfferingId, sectionId, examPeriodId, page, size));
    }

    @PostMapping("/subject-offerings/{offeringId}/periods/{periodId}/publish")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Publish results", description = "Publishes every unpublished result and notifies the students.")
    public ApiResponse<ResultBatchResponse> publish(@PathVariable Long offeringId, @PathVariable Long periodId) {
        return ApiResponse.success("Results published successfully", service.publish(offeringId, periodId));
    }
}
