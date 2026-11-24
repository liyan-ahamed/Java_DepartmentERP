package com.java.erp.modules.system.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.system.dto.response.BatchResponse;
import com.java.erp.modules.system.dto.response.SectionResponse;
import com.java.erp.modules.system.dto.response.SectionStudentResponse;
import com.java.erp.modules.system.service.AcademicStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Academic structure", description = "Read-only batch, section and section-roster lookups (HOD, staff)")
public class AcademicStructureController {

    private final AcademicStructureService service;

    public AcademicStructureController(AcademicStructureService service) {
        this.service = service;
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List active batches")
    public ApiResponse<List<BatchResponse>> listBatches() {
        return ApiResponse.success("Batches fetched successfully", service.listActiveBatches());
    }

    @GetMapping("/sections")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List sections of active batches", description = "Optional filter: batchId.")
    public ApiResponse<List<SectionResponse>> listSections(@RequestParam(required = false) Long batchId) {
        return ApiResponse.success("Sections fetched successfully", service.listActiveSections(batchId));
    }

    @GetMapping("/sections/{sectionId}/students")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Students of a section",
               description = "Every student of the section, with or without a login or enrollments, by register number. "
                       + "active=true (default) or false; search = name or register number. STAFF: only sections they teach.")
    public ApiResponse<PagedResponse<SectionStudentResponse>> sectionStudents(
            @PathVariable Long sectionId,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Section students fetched successfully",
                service.sectionStudents(sectionId, active, search, page, size));
    }
}
