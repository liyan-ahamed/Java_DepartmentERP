package com.java.erp.modules.portfolio.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.modules.portfolio.dto.response.PortfolioSummaryResponse;
import com.java.erp.modules.portfolio.service.PortfolioSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portfolio Analytics (department / section counts). Exists only when
 * {@code features.portfolio-analytics.enabled=true} (env FEATURES_PORTFOLIO_ANALYTICS_ENABLED); default off,
 * so GET /portfolio/summary answers 404. Student portfolios, the review queue, verify/reject and verified
 * student portfolios (PortfolioController) are not affected. The service and DTOs stay for re-enabling.
 */
@RestController
@ConditionalOnProperty(name = "features.portfolio-analytics.enabled", havingValue = "true")
@RequestMapping("/portfolio")
@Tag(name = "Portfolio Analytics", description = "Portfolio counts for HOD / staff (feature flag)")
public class PortfolioAnalyticsController {

    private final PortfolioSummaryService summaryService;

    public PortfolioAnalyticsController(PortfolioSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('STAFF','HOD')")
    @Operation(summary = "Portfolio summary", description = "HOD: whole department. STAFF: sections they teach. Drafts are not counted.")
    public ApiResponse<PortfolioSummaryResponse> summary() {
        return ApiResponse.success("Portfolio summary fetched successfully", summaryService.summary());
    }
}
