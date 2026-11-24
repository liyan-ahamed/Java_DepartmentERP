package com.java.erp.modules.academic.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.academic.dto.request.SubjectOfferingRequest;
import com.java.erp.modules.academic.dto.response.SubjectOfferingResponse;
import com.java.erp.modules.academic.dto.response.SubjectOfferingSummaryResponse;
import com.java.erp.modules.academic.dto.response.TeachingTermsResponse;
import com.java.erp.modules.academic.service.SubjectOfferingService;
import com.java.erp.modules.academic.service.SubjectOfferingSummaryService;
import com.java.erp.modules.academic.service.TeachingTermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/subject-offerings")
@Tag(name = "Subject Offerings", description = "Subject + batch + section + staff + term (HOD manages, staff see their own)")
public class SubjectOfferingController {

    private final SubjectOfferingService service;
    private final SubjectOfferingSummaryService summaryService;
    private final TeachingTermsService teachingTermsService;

    public SubjectOfferingController(SubjectOfferingService service, SubjectOfferingSummaryService summaryService,
                                     TeachingTermsService teachingTermsService) {
        this.service = service;
        this.summaryService = summaryService;
        this.teachingTermsService = teachingTermsService;
    }

    @GetMapping("/teaching-terms")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Cohorts and semesters of my subject offerings (academic selector)",
               description = "Batch -> semester level of the selector. Each cohort has currentSemester and termStatus "
                       + "from the institution's term calendar (admission year + today); each semester is CURRENT, "
                       + "PREVIOUS or UPCOMING. STAFF: only cohorts/semesters with offerings assigned to them (active or "
                       + "not); HOD: all. Classes: GET /subject-offerings?batchId=&semester=.")
    public ApiResponse<List<TeachingTermsResponse>> teachingTerms() {
        return ApiResponse.success("Teaching terms fetched successfully", teachingTermsService.teachingTerms(LocalDate.now()));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a subject offering")
    public ResponseEntity<ApiResponse<SubjectOfferingResponse>> create(@Valid @RequestBody SubjectOfferingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subject offering created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List subject offerings", description = "HOD sees all offerings; STAFF only offerings assigned to them.")
    public ApiResponse<PagedResponse<SubjectOfferingResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Subject offerings fetched successfully",
                service.list(subjectId, batchId, sectionId, staffId, academicYear, semester, active, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Get a subject offering")
    public ApiResponse<SubjectOfferingResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Subject offering fetched successfully", service.get(id));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Headline figures of a subject offering",
               description = "Enrollment count, finalized attendance %, finalized assessments, published results. "
                       + "HOD, or the assigned staff member.")
    public ApiResponse<SubjectOfferingSummaryResponse> summary(@PathVariable Long id) {
        return ApiResponse.success("Subject offering summary fetched successfully", summaryService.summary(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Update a subject offering",
               description = "Staff and active flag can always change; the academic context is fixed once assessments exist.")
    public ApiResponse<SubjectOfferingResponse> update(@PathVariable Long id, @Valid @RequestBody SubjectOfferingRequest request) {
        return ApiResponse.success("Subject offering updated successfully", service.update(id, request));
    }
}
