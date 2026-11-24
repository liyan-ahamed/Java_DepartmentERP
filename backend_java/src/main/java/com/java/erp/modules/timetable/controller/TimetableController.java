package com.java.erp.modules.timetable.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.timetable.dto.request.TimetableSlotRequest;
import com.java.erp.modules.timetable.dto.response.PeriodResponse;
import com.java.erp.modules.timetable.dto.response.TimetableSlotResponse;
import com.java.erp.modules.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/timetable")
@Tag(name = "Timetable", description = "Recurring weekly class timetable of subject offerings")
public class TimetableController {

    private final TimetableService service;

    public TimetableController(TimetableService service) {
        this.service = service;
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAnyRole('HOD','STAFF','STUDENT')")
    @Operation(summary = "Official daily period schedule", description = "8 teaching periods plus break and lunch, in time order.")
    public ApiResponse<List<PeriodResponse>> periods() {
        return ApiResponse.success("Period schedule fetched successfully", service.periods());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List timetable slots (whole week in one call)",
               description = "HOD sees all; STAFF only slots of their own offerings. Filters: batchId, semester, sectionId, staffId, "
                       + "subjectOfferingId, dayOfWeek, active. weekOf=YYYY-MM-DD adds each slot's date in that week and its holidays.")
    public ApiResponse<List<TimetableSlotResponse>> list(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return ApiResponse.success("Timetable fetched successfully",
                service.list(batchId, semester, sectionId, staffId, subjectOfferingId, dayOfWeek, active, weekOf));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('STAFF','STUDENT')")
    @Operation(summary = "My weekly timetable",
               description = "STAFF: own offerings. STUDENT: enrolled offerings. weekOf=YYYY-MM-DD adds dates and holidays.")
    public ApiResponse<List<TimetableSlotResponse>> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return ApiResponse.success("Timetable fetched successfully", service.myTimetable(weekOf));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Get a timetable slot")
    public ApiResponse<TimetableSlotResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Timetable slot fetched successfully", service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a timetable slot",
               description = "One official teaching period (periodNumber 1-8). 400 for break/lunch or unofficial times; "
                       + "409 if it overlaps the section's or staff member's slots.")
    public ResponseEntity<ApiResponse<TimetableSlotResponse>> create(@Valid @RequestBody TimetableSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Timetable slot created successfully", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Update a timetable slot")
    public ApiResponse<TimetableSlotResponse> update(@PathVariable Long id, @Valid @RequestBody TimetableSlotRequest request) {
        return ApiResponse.success("Timetable slot updated successfully", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Delete (deactivate) a timetable slot")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("Timetable slot deleted successfully");
    }
}
