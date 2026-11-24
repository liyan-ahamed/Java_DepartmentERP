package com.java.erp.modules.feedback.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.feedback.dto.request.FeedbackFormRequest;
import com.java.erp.modules.feedback.dto.request.SubmitFeedbackRequest;
import com.java.erp.modules.feedback.dto.response.FeedbackAnalyticsResponse;
import com.java.erp.modules.feedback.dto.response.FeedbackFormResponse;
import com.java.erp.modules.feedback.dto.response.FeedbackResponsesResponse;
import com.java.erp.modules.feedback.entity.FeedbackForm;
import com.java.erp.modules.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HOD manages forms and sees results; STUDENT answers targeted forms once; STAFF have no access. */
@RestController
@RequestMapping("/feedback")
@Tag(name = "Feedback", description = "Academic feedback forms (anonymous or identified)")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping("/forms")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a DRAFT feedback form with its questions")
    public ResponseEntity<ApiResponse<FeedbackFormResponse>> create(@Valid @RequestBody FeedbackFormRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Feedback form created successfully", service.create(request)));
    }

    @GetMapping("/forms")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "List feedback forms")
    public ApiResponse<PagedResponse<FeedbackFormResponse>> list(
            @RequestParam(required = false) FeedbackForm.Status status,
            @RequestParam(required = false) FeedbackForm.TargetType targetType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Feedback forms fetched successfully", service.list(status, targetType, page, size));
    }

    @GetMapping("/forms/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Feedback forms for me", description = "Open forms targeted to me, and closed forms I submitted.")
    public ApiResponse<List<FeedbackFormResponse>> mine() {
        return ApiResponse.success("Feedback forms fetched successfully", service.myForms());
    }

    @GetMapping("/forms/{id}")
    @PreAuthorize("hasAnyRole('HOD','STUDENT')")
    @Operation(summary = "Get a feedback form")
    public ApiResponse<FeedbackFormResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Feedback form fetched successfully", service.get(id));
    }

    @PutMapping("/forms/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Edit a DRAFT feedback form (questions are replaced)")
    public ApiResponse<FeedbackFormResponse> update(@PathVariable Long id, @Valid @RequestBody FeedbackFormRequest request) {
        return ApiResponse.success("Feedback form updated successfully", service.update(id, request));
    }

    @PostMapping("/forms/{id}/open")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Open a form (DRAFT -> OPEN)", description = "Targeted students are notified.")
    public ApiResponse<FeedbackFormResponse> open(@PathVariable Long id) {
        return ApiResponse.success("Feedback form opened successfully", service.open(id));
    }

    @PostMapping("/forms/{id}/close")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Close a form (OPEN -> CLOSED)")
    public ApiResponse<FeedbackFormResponse> close(@PathVariable Long id) {
        return ApiResponse.success("Feedback form closed successfully", service.close(id));
    }

    @GetMapping("/forms/{id}/analytics")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Aggregate results", description = "Average and distribution per rating question; text answers without identity.")
    public ApiResponse<FeedbackAnalyticsResponse> analytics(@PathVariable Long id) {
        return ApiResponse.success("Feedback analytics fetched successfully", service.analytics(id));
    }

    @GetMapping("/forms/{id}/responses")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Individual responses", description = "Anonymous forms: no student, no time, shuffled.")
    public ApiResponse<FeedbackResponsesResponse> responses(@PathVariable Long id) {
        return ApiResponse.success("Feedback responses fetched successfully", service.responses(id));
    }

    @PostMapping("/forms/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit my answers (once)")
    public ApiResponse<FeedbackFormResponse> submit(@PathVariable Long id, @Valid @RequestBody SubmitFeedbackRequest request) {
        return ApiResponse.success("Feedback submitted successfully", service.submit(id, request));
    }
}
