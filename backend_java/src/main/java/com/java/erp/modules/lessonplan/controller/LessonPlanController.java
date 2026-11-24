package com.java.erp.modules.lessonplan.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.lessonplan.config.ConditionalOnLessonPlansEnabled;
import com.java.erp.modules.lessonplan.dto.request.CompleteLessonPlanRequest;
import com.java.erp.modules.lessonplan.dto.request.CreateLessonPlanRequest;
import com.java.erp.modules.lessonplan.dto.request.UpdateLessonPlanRequest;
import com.java.erp.modules.lessonplan.dto.response.LessonPlanProgressResponse;
import com.java.erp.modules.lessonplan.dto.response.LessonPlanResponse;
import com.java.erp.modules.lessonplan.entity.LessonPlan;
import com.java.erp.modules.lessonplan.service.LessonPlanService;
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
@ConditionalOnLessonPlansEnabled
@RequestMapping("/lesson-plans")
@Tag(name = "Lesson Plan", description = "Planned topics per subject offering and completion progress")
public class LessonPlanController {

    private final LessonPlanService service;

    public LessonPlanController(LessonPlanService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Plan a topic for one of your subject offerings")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> create(@Valid @RequestBody CreateLessonPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lesson plan created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List lesson plans", description = "HOD sees all; STAFF only plans of their own offerings.")
    public ApiResponse<PagedResponse<LessonPlanResponse>> list(
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) LessonPlan.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Lesson plans fetched successfully",
                service.list(subjectOfferingId, sectionId, subjectId, staffId, status, page, size));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Lesson plans of my enrolled subjects")
    public ApiResponse<List<LessonPlanResponse>> mine(@RequestParam(required = false) Long subjectOfferingId) {
        return ApiResponse.success("Lesson plans fetched successfully", service.myPlans(subjectOfferingId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF','STUDENT')")
    @Operation(summary = "Get a lesson plan")
    public ApiResponse<LessonPlanResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Lesson plan fetched successfully", service.get(id));
    }

    @GetMapping("/subject-offerings/{offeringId}/progress")
    @PreAuthorize("hasAnyRole('HOD','STAFF','STUDENT')")
    @Operation(summary = "Lesson plan progress of a subject offering")
    public ApiResponse<LessonPlanProgressResponse> progress(@PathVariable Long offeringId) {
        return ApiResponse.success("Lesson plan progress fetched successfully", service.progress(offeringId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Edit a PLANNED topic")
    public ApiResponse<LessonPlanResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateLessonPlanRequest request) {
        return ApiResponse.success("Lesson plan updated successfully", service.update(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Mark a topic as completed")
    public ApiResponse<LessonPlanResponse> complete(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) CompleteLessonPlanRequest request) {
        return ApiResponse.success("Lesson plan completed successfully", service.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Cancel a planned topic")
    public ApiResponse<LessonPlanResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success("Lesson plan cancelled successfully", service.cancel(id));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Set a completed or cancelled topic back to PLANNED")
    public ApiResponse<LessonPlanResponse> reopen(@PathVariable Long id) {
        return ApiResponse.success("Lesson plan reopened successfully", service.reopen(id));
    }
}
