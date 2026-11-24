package com.java.erp.modules.gradebook.controller;

import com.java.erp.modules.gradebook.config.ConditionalOnGradebookEnabled;
import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.gradebook.dto.response.InternalMarksResponse;
import com.java.erp.modules.gradebook.service.InternalMarksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only internal-assessment data. Calculation formulas are not configured yet, so totals
 * are null and calculationStatus is PENDING_CALCULATION_CONFIGURATION.
 */
@RestController
@ConditionalOnGradebookEnabled
@Tag(name = "Internal Marks", description = "Internal-assessment summaries (calculation not configured yet)")
public class InternalMarksController {

    private final InternalMarksService service;

    public InternalMarksController(InternalMarksService service) {
        this.service = service;
    }

    @GetMapping("/internal-marks/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My internal marks", description = "Optional filters: subjectId, academicYear, semester.")
    public ApiResponse<List<InternalMarksResponse>> mine(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester) {
        return ApiResponse.success("Internal marks fetched successfully", service.mine(subjectId, academicYear, semester));
    }

    @GetMapping("/subject-offerings/{id}/internal-marks")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Internal marks of a subject offering", description = "HOD: any offering. STAFF: own offerings only.")
    public ApiResponse<InternalMarksResponse> forOffering(@PathVariable Long id) {
        return ApiResponse.success("Internal marks fetched successfully", service.forOffering(id));
    }
}
