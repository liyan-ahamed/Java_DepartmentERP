package com.java.erp.modules.attendance.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.attendance.dto.request.BulkAttendanceRequest;
import com.java.erp.modules.attendance.dto.request.CreateAttendanceSessionRequest;
import com.java.erp.modules.attendance.dto.request.UpdateAttendanceSessionRequest;
import com.java.erp.modules.attendance.dto.response.*;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.service.AttendanceDayService;
import com.java.erp.modules.attendance.service.AttendanceOverviewService;
import com.java.erp.modules.attendance.service.AttendanceSessionService;
import com.java.erp.modules.attendance.service.AttendanceSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Academic attendance. STAFF manage sessions of their own offerings; HOD reads everything;
 * STUDENT sees only their own summary (FINALIZED sessions).
 */
@RestController
@RequestMapping("/attendance")
@Tag(name = "Attendance", description = "Attendance sessions, bulk marking, finalization and summaries")
public class AttendanceController {

    private final AttendanceSessionService sessions;
    private final AttendanceSummaryService summaries;
    private final AttendanceDayService day;
    private final AttendanceOverviewService overview;

    public AttendanceController(AttendanceSessionService sessions, AttendanceSummaryService summaries,
                                AttendanceDayService day, AttendanceOverviewService overview) {
        this.sessions = sessions;
        this.summaries = summaries;
        this.day = day;
        this.overview = overview;
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create a DRAFT attendance session for one of your subject offerings")
    public ResponseEntity<ApiResponse<AttendanceSessionResponse>> create(@Valid @RequestBody CreateAttendanceSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attendance session created successfully", sessions.create(request)));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "List attendance sessions", description = "HOD sees all; STAFF only sessions of their offerings.")
    public ApiResponse<PagedResponse<AttendanceSessionResponse>> list(
            @RequestParam(required = false) Long subjectOfferingId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) AttendanceSession.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Attendance sessions fetched successfully",
                sessions.list(subjectOfferingId, sectionId, status, from, to, page, size));
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Get an attendance session")
    public ApiResponse<AttendanceSessionResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Attendance session fetched successfully", sessions.get(id));
    }

    @PutMapping("/sessions/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Update a DRAFT attendance session")
    public ApiResponse<AttendanceSessionResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAttendanceSessionRequest request) {
        return ApiResponse.success("Attendance session updated successfully", sessions.update(id, request));
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Delete a DRAFT attendance session and its marks")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sessions.delete(id);
        return ApiResponse.success("Attendance session deleted successfully");
    }

    @GetMapping("/sessions/{id}/records")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Full roster of a session", description = "Every enrolled student; unmarked students show NOT_MARKED.")
    public ApiResponse<SessionRecordsResponse> records(@PathVariable Long id) {
        return ApiResponse.success("Attendance records fetched successfully", sessions.records(id));
    }

    @PutMapping("/sessions/{id}/records")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Bulk mark attendance of a DRAFT session",
               description = "All rows are validated first; any invalid row rejects the whole request.")
    public ApiResponse<BulkAttendanceResponse> saveRecords(@PathVariable Long id, @Valid @RequestBody BulkAttendanceRequest request) {
        return ApiResponse.success("Attendance saved successfully", sessions.saveRecords(id, request));
    }

    @PostMapping("/sessions/{id}/finalize")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Finalize a session", description = "Fails while any enrolled student is unmarked. Finalized sessions are immutable.")
    public ApiResponse<AttendanceSessionResponse> finalizeSession(@PathVariable Long id) {
        return ApiResponse.success("Attendance session finalized successfully", sessions.finalizeSession(id));
    }

    @GetMapping("/subject-offerings/{offeringId}/summary")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Class attendance summary", description = "One row per enrolled student (FINALIZED sessions only).")
    public ApiResponse<ClassAttendanceSummaryResponse> classSummary(@PathVariable Long offeringId) {
        return ApiResponse.success("Attendance summary fetched successfully", summaries.classSummary(offeringId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My attendance per subject", description = "FINALIZED sessions only.")
    public ApiResponse<List<SubjectAttendanceResponse>> mine() {
        return ApiResponse.success("Attendance fetched successfully", summaries.mySummary());
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My attendance per active subject plus Overall",
               description = "FINALIZED sessions only. Overall is computed from summed counts, not averaged percentages.")
    public ApiResponse<StudentAttendanceOverviewResponse> myOverview() {
        return ApiResponse.success("Attendance fetched successfully", overview.mine());
    }

    @GetMapping("/day")
    @PreAuthorize("hasAnyRole('HOD','STAFF','STUDENT')")
    @Operation(summary = "Scheduled classes of one date with their attendance sessions",
               description = "HOD: sectionId required (batchId, semester optional). STAFF: own classes only. STUDENT: own classes "
                       + "with myAttendanceStatus (FINALIZED sessions only). Holidays are flagged; slots are never removed.")
    public ApiResponse<AttendanceDayResponse> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long sectionId) {
        return ApiResponse.success("Attendance day fetched successfully", day.day(date, batchId, semester, sectionId));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HOD','STAFF')")
    @Operation(summary = "Class attendance per subject plus Overall",
               description = "HOD: sectionId required (batchId, semester optional). STAFF: own offerings only. Percentages are "
                       + "attended records / eligible records for the whole class.")
    public ApiResponse<ClassAttendanceOverviewResponse> classOverview(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long sectionId) {
        return ApiResponse.success("Attendance summary fetched successfully", overview.classOverview(batchId, semester, sectionId));
    }
}
