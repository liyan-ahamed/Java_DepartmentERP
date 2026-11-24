package com.java.erp.modules.exams.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.exams.dto.request.ExamPeriodRequest;
import com.java.erp.modules.exams.dto.response.ExamPeriodResponse;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.service.ExamPeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam-periods")
@Tag(name = "Exam Periods", description = "Examination periods and their registration windows (HOD manages, staff and COE read)")
public class ExamPeriodController {

    private final ExamPeriodService service;

    public ExamPeriodController(ExamPeriodService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a DRAFT exam period")
    public ResponseEntity<ApiResponse<ExamPeriodResponse>> create(@Valid @RequestBody ExamPeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Exam period created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF','COE')")
    @Operation(summary = "List exam periods")
    public ApiResponse<PagedResponse<ExamPeriodResponse>> list(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) ExamPeriod.ExamType examType,
            @RequestParam(required = false) ExamPeriod.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Exam periods fetched successfully",
                service.list(academicYear, semester, examType, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF','COE')")
    @Operation(summary = "Get an exam period")
    public ApiResponse<ExamPeriodResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Exam period fetched successfully", service.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Update an exam period", description = "DRAFT: all fields. OPEN: name and dates only. CLOSED: none.")
    public ApiResponse<ExamPeriodResponse> update(@PathVariable Long id, @Valid @RequestBody ExamPeriodRequest request) {
        return ApiResponse.success("Exam period updated successfully", service.update(id, request));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Open registration (DRAFT -> OPEN)", description = "Notifies enrolled students of the term.")
    public ApiResponse<ExamPeriodResponse> open(@PathVariable Long id) {
        return ApiResponse.success("Exam period opened successfully", service.open(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Close registration (OPEN -> CLOSED)")
    public ApiResponse<ExamPeriodResponse> close(@PathVariable Long id) {
        return ApiResponse.success("Exam period closed successfully", service.close(id));
    }
}
