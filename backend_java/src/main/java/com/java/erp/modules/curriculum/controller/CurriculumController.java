package com.java.erp.modules.curriculum.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.curriculum.dto.request.CurriculumRequest;
import com.java.erp.modules.curriculum.dto.response.CurriculumResponse;
import com.java.erp.modules.curriculum.service.CurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/curricula/batches/{batchId}/semesters/{semester}")
@Tag(name = "Curriculum", description = "Registration requirements per cohort + semester (HOD configures, staff read)")
public class CurriculumController {

    private final CurriculumService service;

    public CurriculumController(CurriculumService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Get the curriculum", description = "configured=false when nothing is saved for this cohort + semester.")
    public ApiResponse<CurriculumResponse> get(@PathVariable Long batchId, @PathVariable Integer semester) {
        return ApiResponse.success("Curriculum fetched successfully", service.get(batchId, semester));
    }

    @PutMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Save the whole curriculum",
               description = "All-or-nothing replace. version: null to create, else the stored version (409 when stale).")
    public ApiResponse<CurriculumResponse> replace(@PathVariable Long batchId, @PathVariable Integer semester,
                                                   @RequestBody CurriculumRequest request) {
        return ApiResponse.success("Curriculum saved successfully", service.replace(batchId, semester, request));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Remove the curriculum", description = "Requirements become unknown again (configured=false).")
    public ApiResponse<CurriculumResponse> delete(@PathVariable Long batchId, @PathVariable Integer semester) {
        return ApiResponse.success("Curriculum removed successfully", service.delete(batchId, semester));
    }
}
