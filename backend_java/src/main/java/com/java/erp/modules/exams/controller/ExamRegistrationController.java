package com.java.erp.modules.exams.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.exams.dto.request.ReviewRegistrationRequest;
import com.java.erp.modules.exams.dto.request.SaveRegistrationRequest;
import com.java.erp.modules.exams.dto.response.EligibleSubjectResponse;
import com.java.erp.modules.exams.dto.response.ExamRegistrationResponse;
import com.java.erp.modules.exams.dto.response.StudentExamPeriodResponse;
import com.java.erp.modules.exams.entity.ExamRegistration;
import com.java.erp.modules.exams.service.ExamRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** STUDENT: own registration only. HOD: review. STAFF: no access. */
@RestController
@RequestMapping("/exam-registrations")
@Tag(name = "Exam Registration", description = "Students register enrolled subjects for an exam period; HOD approves or rejects")
public class ExamRegistrationController {

    private final ExamRegistrationService service;

    public ExamRegistrationController(ExamRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/periods")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Open exam periods I can register for")
    public ApiResponse<List<StudentExamPeriodResponse>> openPeriods() {
        return ApiResponse.success("Exam periods fetched successfully", service.openPeriods());
    }

    @GetMapping("/periods/{periodId}/eligible-subjects")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My eligible (enrolled) subjects for an exam period")
    public ApiResponse<List<EligibleSubjectResponse>> eligibleSubjects(@PathVariable Long periodId) {
        return ApiResponse.success("Eligible subjects fetched successfully", service.eligibleSubjects(periodId));
    }

    @PutMapping("/periods/{periodId}/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Choose subjects (creates or updates my DRAFT registration)")
    public ApiResponse<ExamRegistrationResponse> saveMine(@PathVariable Long periodId, @Valid @RequestBody SaveRegistrationRequest request) {
        return ApiResponse.success("Exam registration saved successfully", service.saveMine(periodId, request));
    }

    @PostMapping("/periods/{periodId}/me/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit my registration")
    public ApiResponse<ExamRegistrationResponse> submitMine(@PathVariable Long periodId) {
        return ApiResponse.success("Exam registration submitted successfully", service.submitMine(periodId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My exam registrations")
    public ApiResponse<List<ExamRegistrationResponse>> mine() {
        return ApiResponse.success("Exam registrations fetched successfully", service.myRegistrations());
    }

    @GetMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "List exam registrations")
    public ApiResponse<PagedResponse<ExamRegistrationResponse>> list(
            @RequestParam(required = false) Long examPeriodId,
            @RequestParam(required = false) ExamRegistration.Status status,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Exam registrations fetched successfully",
                service.list(examPeriodId, status, sectionId, studentId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STUDENT')")
    @Operation(summary = "Get an exam registration", description = "Students can open their own only.")
    public ApiResponse<ExamRegistrationResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Exam registration fetched successfully", service.get(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Approve a SUBMITTED registration")
    public ApiResponse<ExamRegistrationResponse> approve(@PathVariable Long id,
                                                         @Valid @RequestBody(required = false) ReviewRegistrationRequest request) {
        return ApiResponse.success("Exam registration approved successfully", service.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Reject a SUBMITTED registration (reviewNote required)")
    public ApiResponse<ExamRegistrationResponse> reject(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) ReviewRegistrationRequest request) {
        return ApiResponse.success("Exam registration rejected successfully", service.reject(id, request));
    }
}
