package com.java.erp.modules.enrollment.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.enrollment.dto.request.EnrollStudentRequest;
import com.java.erp.modules.enrollment.dto.request.UpdateEnrollmentStatusRequest;
import com.java.erp.modules.enrollment.dto.response.EnrollmentResponse;
import com.java.erp.modules.enrollment.dto.response.SectionEnrollmentResponse;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.service.EnrollmentService;
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

@RestController
@RequestMapping("/course-enrollments")
@Tag(name = "Course Enrollment", description = "Students enrolled in subject offerings (HOD manages, staff read their own, students see theirs)")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List enrollments", description = "HOD sees all; STAFF only enrollments of their own offerings.")
    public ApiResponse<PagedResponse<EnrollmentResponse>> list(
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) CourseEnrollment.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Enrollments fetched successfully",
                service.list(subjectOfferingId, sectionId, studentId, status, page, size));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My enrollments")
    public ApiResponse<List<EnrollmentResponse>> mine() {
        return ApiResponse.success("Enrollments fetched successfully", service.myEnrollments());
    }

    @GetMapping("/subject-offerings/{offeringId}")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Roster of a subject offering", description = "Every enrollment row (any status).")
    public ApiResponse<List<EnrollmentResponse>> roster(@PathVariable Long offeringId) {
        return ApiResponse.success("Roster fetched successfully", service.roster(offeringId));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Enroll one student in a subject offering")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(@Valid @RequestBody EnrollStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student enrolled successfully", service.enrollStudent(request)));
    }

    @PostMapping("/subject-offerings/{offeringId}/enroll-section")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Enroll the offering's whole section",
               description = "Creates enrollments for active students of the section that have none yet; idempotent.")
    public ApiResponse<SectionEnrollmentResponse> enrollSection(@PathVariable Long offeringId) {
        return ApiResponse.success("Section enrolled successfully", service.enrollSection(offeringId));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Drop, reactivate or complete an enrollment")
    public ApiResponse<EnrollmentResponse> changeStatus(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateEnrollmentStatusRequest request) {
        return ApiResponse.success("Enrollment updated successfully", service.changeStatus(id, request.status()));
    }
}
