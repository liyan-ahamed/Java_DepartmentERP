package com.java.erp.modules.calendar.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.calendar.dto.*;
import com.java.erp.modules.calendar.service.CalendarService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/calendar")
public class CalendarController {
    private final CalendarService service;
    public CalendarController(CalendarService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','STUDENT','HOD')")
    public ApiResponse<CalendarResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.success("Calendar fetched successfully", service.list(dateFrom, dateTo));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<CalendarItemResponse>> create(@Valid @RequestBody CalendarEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Calendar entry created", service.create(request)));
    }
}
