package com.java.erp.modules.academic.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.academic.dto.request.SubjectRequest;
import com.java.erp.modules.academic.dto.response.SubjectResponse;
import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.service.SubjectService;
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
@RequestMapping("/subjects")
@Tag(name = "Subjects", description = "Department subjects (HOD manages, staff read)")
public class SubjectController {

    private final SubjectService service;

    public SubjectController(SubjectService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a subject")
    public ResponseEntity<ApiResponse<SubjectResponse>> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subject created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List subjects",
               description = "Optional filters: subjectType, active, search (code or name), semester and batchId "
                       + "(subjects with an active offering in that semester / for that cohort).")
    public ApiResponse<PagedResponse<SubjectResponse>> list(
            @RequestParam(required = false) Subject.SubjectType subjectType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @Min(1) @Max(8) Integer semester,
            @RequestParam(required = false) Long batchId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Subjects fetched successfully",
                service.list(subjectType, active, search, semester, batchId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Get a subject")
    public ApiResponse<SubjectResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Subject fetched successfully", service.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Update a subject", description = "The subject type cannot change once the subject has offerings.")
    public ApiResponse<SubjectResponse> update(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        return ApiResponse.success("Subject updated successfully", service.update(id, request));
    }
}
