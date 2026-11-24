package com.java.erp.modules.gradebook.controller;

import com.java.erp.modules.gradebook.config.ConditionalOnGradebookEnabled;
import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.gradebook.dto.request.BulkGradeRequest;
import com.java.erp.modules.gradebook.dto.request.CreateAssessmentRequest;
import com.java.erp.modules.gradebook.dto.request.GradeImportRequest;
import com.java.erp.modules.gradebook.dto.request.MarksEntryDeadlineRequest;
import com.java.erp.modules.gradebook.dto.request.UpdateAssessmentRequest;
import com.java.erp.modules.gradebook.dto.response.AssessmentOverviewResponse;
import com.java.erp.modules.gradebook.dto.response.AssessmentResponse;
import com.java.erp.modules.gradebook.dto.response.GradeImportPreviewResponse;
import com.java.erp.modules.gradebook.dto.response.AssessmentSummaryResponse;
import com.java.erp.modules.gradebook.dto.response.GradeBulkUpdateResponse;
import com.java.erp.modules.gradebook.dto.response.GradebookResponse;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.service.AssessmentOverviewService;
import com.java.erp.modules.gradebook.service.AssessmentService;
import com.java.erp.modules.gradebook.service.GradeImportService;
import com.java.erp.modules.gradebook.service.GradeService;
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

/**
 * Assessments of subject offerings. Changes are limited to the STAFF member assigned to the
 * offering (checked in the services); HOD has read access to everything; students have none.
 */
@RestController
@ConditionalOnGradebookEnabled
@RequestMapping("/assessments")
@Tag(name = "Assessments", description = "Offline assessments, bulk grade entry, finalization and analytics")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final GradeService gradeService;
    private final AssessmentOverviewService overviewService;
    private final GradeImportService importService;

    public AssessmentController(AssessmentService assessmentService, GradeService gradeService,
                                AssessmentOverviewService overviewService, GradeImportService importService) {
        this.assessmentService = assessmentService;
        this.gradeService = gradeService;
        this.overviewService = overviewService;
        this.importService = importService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create a DRAFT assessment for one of your subject offerings")
    public ResponseEntity<ApiResponse<AssessmentResponse>> create(@Valid @RequestBody CreateAssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assessment created successfully", assessmentService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List assessments", description = "HOD sees all; STAFF only assessments of their offerings.")
    public ApiResponse<PagedResponse<AssessmentResponse>> list(
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Assessment.AssessmentType assessmentType,
            @RequestParam(required = false) Assessment.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Assessments fetched successfully", assessmentService.list(
                subjectOfferingId, subjectId, batchId, sectionId, academicYear, semester, assessmentType, status, page, size));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Assessments of one cohort + semester grouped by subject offering, with statistics",
               description = "Per offering: class size and every assessment (DRAFT and FINALIZED) with expected/graded/"
                       + "absent/exempted/not-graded counts, average/highest/lowest (GRADED marks only; absent is never "
                       + "zero), pass figures, finalizedAt and readyToFinalize. STAFF: own offerings only.")
    public ApiResponse<List<AssessmentOverviewResponse>> overview(
            @RequestParam Long batchId,
            @RequestParam @Min(1) @Max(8) int semester,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long subjectOfferingId) {
        return ApiResponse.success("Assessment overview fetched successfully",
                overviewService.overview(batchId, semester, sectionId, subjectOfferingId));
    }

    @PutMapping("/{id}/marks-entry-deadline")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Set the marks-entry deadline of an assessment (HOD)",
               description = "Body {\"marksEntryDueAt\": \"2026-10-20T17:00:00+05:30\"} or null to clear. Only the deadline changes; "
                       + "409 once the assessment is finalized. The assigned staff member reads it on the assessment.")
    public ApiResponse<AssessmentResponse> setMarksEntryDeadline(@PathVariable Long id,
                                                                 @RequestBody MarksEntryDeadlineRequest request) {
        return ApiResponse.success("Marks-entry deadline updated successfully",
                assessmentService.setMarksEntryDeadline(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Get an assessment")
    public ApiResponse<AssessmentResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Assessment fetched successfully", assessmentService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Update a DRAFT assessment")
    public ApiResponse<AssessmentResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAssessmentRequest request) {
        return ApiResponse.success("Assessment updated successfully", assessmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Delete a DRAFT assessment and its grades")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        assessmentService.delete(id);
        return ApiResponse.success("Assessment deleted successfully");
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Finalize (publish) an assessment",
               description = "Makes grades visible to students and notifies them. Finalized assessments cannot be changed.")
    public ApiResponse<AssessmentResponse> finalizeAssessment(@PathVariable Long id) {
        return ApiResponse.success("Assessment finalized successfully", assessmentService.finalizeAssessment(id));
    }

    @PutMapping("/{id}/grades")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Bulk create/update/clear grades of a DRAFT assessment",
               description = "Send only changed rows (autosave). status + marks null clears a grade. All rows are "
                       + "validated first; any invalid row rejects the whole request with a list of errors.")
    public ApiResponse<GradeBulkUpdateResponse> saveGrades(@PathVariable Long id, @Valid @RequestBody BulkGradeRequest request) {
        return ApiResponse.success("Grades saved successfully", gradeService.saveGrades(id, request));
    }

    @PostMapping("/{id}/grades/import/preview")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Preview a spreadsheet import (nothing is saved)",
               description = "Rows matched by register number: NEW / OVERWRITE / UNCHANGED / SKIPPED / UNMATCHED / "
                       + "DUPLICATE / INVALID, plus class students missing from the file.")
    public ApiResponse<GradeImportPreviewResponse> previewImport(@PathVariable Long id,
                                                                 @Valid @RequestBody GradeImportRequest request) {
        return ApiResponse.success("Import preview ready", importService.preview(id, request));
    }

    @PostMapping("/{id}/grades/import")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Apply a spreadsheet import (all or nothing)",
               description = "400 if any row is unmatched, duplicated or invalid; 409 unless confirmOverwrite=true when "
                       + "existing grades would change. Blank rows never clear a grade.")
    public ApiResponse<GradeBulkUpdateResponse> applyImport(@PathVariable Long id,
                                                            @Valid @RequestBody GradeImportRequest request) {
        return ApiResponse.success("Grades imported successfully", importService.apply(id, request));
    }

    @GetMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Gradebook of an assessment", description = "Every expected student, including those not graded yet.")
    public ApiResponse<GradebookResponse> gradebook(@PathVariable Long id) {
        return ApiResponse.success("Gradebook fetched successfully", gradeService.gradebook(id));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Assessment statistics")
    public ApiResponse<AssessmentSummaryResponse> summary(@PathVariable Long id) {
        return ApiResponse.success("Assessment summary fetched successfully", gradeService.summary(id));
    }
}
