package com.java.erp.modules.holiday.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.holiday.dto.request.HolidayRequest;
import com.java.erp.modules.holiday.dto.response.HolidayCalendarResponse;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.dto.response.HolidaySyncResponse;
import com.java.erp.modules.holiday.entity.Holiday;
import com.java.erp.modules.holiday.service.HolidayCalendarService;
import com.java.erp.modules.holiday.service.HolidayService;
import com.java.erp.modules.holiday.service.HolidaySyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/holidays")
@Tag(name = "Holidays", description = "College holiday calendar (HOD manages and publishes); government lists for reference")
public class HolidayController {

    private final HolidayService service;
    private final HolidayCalendarService calendarService;
    private final HolidaySyncService syncService;

    public HolidayController(HolidayService service, HolidayCalendarService calendarService, HolidaySyncService syncService) {
        this.service = service;
        this.calendarService = calendarService;
        this.syncService = syncService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD','STAFF','STUDENT')")
    @Operation(summary = "List holidays",
               description = "Default: college holidays (STAFF/STUDENT: published years only; HOD: drafts too). "
                       + "scope=NATIONAL|TAMIL_NADU returns that government reference list. Filters: year (default: current "
                       + "year when no date range is given), dateFrom, dateTo, batchId (holidays applying to that cohort).")
    public ApiResponse<List<HolidayResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Holiday.Scope scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long batchId) {
        return ApiResponse.success("Holidays fetched successfully", service.list(year, scope, dateFrom, dateTo, batchId));
    }

    @GetMapping("/calendars/{year}")
    @PreAuthorize("hasAnyRole('HOD','STAFF','STUDENT')")
    @Operation(summary = "Publication state of a calendar year",
               description = "HOD: NOT_CREATED | DRAFT | PUBLISHED. STAFF/STUDENT: PUBLISHED or NOT_PUBLISHED.")
    public ApiResponse<HolidayCalendarResponse> calendar(@PathVariable int year) {
        return ApiResponse.success("Holiday calendar fetched successfully", calendarService.calendar(year));
    }

    @PostMapping("/calendars/{year}/publish")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Publish a calendar year", description = "Allowed with no holidays. Already published -> 409.")
    public ApiResponse<HolidayCalendarResponse> publish(@PathVariable int year) {
        return ApiResponse.success("Holiday calendar published", calendarService.publish(year));
    }

    @PostMapping("/calendars/{year}/unpublish")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Return a published calendar year to draft", description = "Not published -> 409.")
    public ApiResponse<HolidayCalendarResponse> unpublish(@PathVariable int year) {
        return ApiResponse.success("Holiday calendar unpublished", calendarService.unpublish(year));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Add a college holiday", description = "A year without a calendar gets a DRAFT calendar.")
    public ResponseEntity<ApiResponse<HolidayResponse>> create(@Valid @RequestBody HolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Holiday created successfully", calendarService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Replace a college holiday", description = "Government reference rows are read-only (409).")
    public ApiResponse<HolidayResponse> update(@PathVariable Long id, @Valid @RequestBody HolidayRequest request) {
        return ApiResponse.success("Holiday updated successfully", calendarService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Delete a college holiday", description = "Government reference rows are read-only (409).")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        calendarService.delete(id);
        return ApiResponse.success("Holiday deleted successfully", null);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Sync one year of holidays from the sources",
               description = "Idempotent. Government lists are refreshed for reference. The college calendar file is "
                       + "imported only for a year that has no calendar yet. Unavailable sources keep cached rows.")
    public ApiResponse<HolidaySyncResponse> sync(@RequestParam int year) {
        return ApiResponse.success("Holiday sync finished", syncService.sync(year));
    }
}
