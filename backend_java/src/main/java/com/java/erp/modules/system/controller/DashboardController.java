package com.java.erp.modules.system.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.system.dto.response.DashboardSummaryResponse;
import com.java.erp.modules.system.dto.response.StaffDashboardResponse;
import com.java.erp.modules.system.service.DashboardService;
import com.java.erp.modules.system.service.StaffDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for dashboard summary endpoints.
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Endpoints for dashboard summary data")
public class DashboardController {

    private final DashboardService dashboardService;
    private final StaffDashboardService staffDashboardService;

    public DashboardController(DashboardService dashboardService, StaffDashboardService staffDashboardService) {
        this.dashboardService = dashboardService;
        this.staffDashboardService = staffDashboardService;
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Staff dashboard",
               description = "Own upcoming classes (next days, holidays skipped) and things to complete "
                       + "(draft attendance to finalize, marks entry with a deadline). No department-wide figures.")
    public ResponseEntity<ApiResponse<StaffDashboardResponse>> getStaffDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Staff dashboard fetched successfully", staffDashboardService.dashboard()));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HOD', 'STAFF', 'STUDENT')")
    @Operation(summary = "Get dashboard summary",
               description = "Returns total students, total staff, year-wise and section-wise student counts")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Dashboard summary fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary() {
        DashboardSummaryResponse summary = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary fetched successfully", summary));
    }
}
