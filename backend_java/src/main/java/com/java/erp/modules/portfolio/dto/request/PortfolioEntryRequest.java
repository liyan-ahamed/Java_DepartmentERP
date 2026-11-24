package com.java.erp.modules.portfolio.dto.request;

import com.java.erp.modules.portfolio.entity.PortfolioEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create a draft or fully replace a DRAFT/REJECTED entry. Only activityType and title are needed
 * for a draft; organization, description, level and startDate are required when it is submitted.
 * endDate >= startDate and the evidenceUrl format (http/https) are checked in the service.
 */
public record PortfolioEntryRequest(
        @NotNull PortfolioEntry.ActivityType activityType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String organization,
        @Size(max = 2000) String description,
        PortfolioEntry.Level level,
        @Size(max = 100) String roleOrPosition,
        @Size(max = 150) String achievementResult,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500) String evidenceUrl
) {
}
