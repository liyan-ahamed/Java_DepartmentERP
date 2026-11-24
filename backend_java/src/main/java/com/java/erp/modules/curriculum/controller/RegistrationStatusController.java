package com.java.erp.modules.curriculum.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.curriculum.dto.response.RegistrationStatusResponses.Detail;
import com.java.erp.modules.curriculum.dto.response.RegistrationStatusResponses.StudentRow;
import com.java.erp.modules.curriculum.dto.response.RegistrationStatusResponses.Summary;
import com.java.erp.modules.curriculum.service.RegistrationEvaluator;
import com.java.erp.modules.curriculum.service.RegistrationStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/course-enrollments/registration-status")
@Tag(name = "Registration status", description = "Semester registration completeness per student, against the curriculum (HOD)")
public class RegistrationStatusController {

    private final RegistrationStatusService service;

    public RegistrationStatusController(RegistrationStatusService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "One row per active student of the class",
               description = "Filters registrationStatus and search apply to the whole class before paging. sectionId optional.")
    public ApiResponse<PagedResponse<StudentRow>> list(
            @RequestParam Long batchId,
            @RequestParam Integer semester,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) RegistrationEvaluator.Status registrationStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Registration status fetched successfully",
                service.list(batchId, semester, sectionId, registrationStatus, search, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Counts per registration status for the class")
    public ApiResponse<Summary> summary(@RequestParam Long batchId, @RequestParam Integer semester,
                                        @RequestParam(required = false) Long sectionId) {
        return ApiResponse.success("Registration summary fetched successfully", service.summary(batchId, semester, sectionId));
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "One student's registration against the curriculum")
    public ApiResponse<Detail> detail(@PathVariable Long studentId, @RequestParam Integer semester,
                                      @RequestParam(required = false) Long batchId) {
        return ApiResponse.success("Registration detail fetched successfully", service.detail(studentId, semester, batchId));
    }
}
