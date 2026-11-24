package com.java.erp.modules.holiday.dto.request;

import com.java.erp.modules.holiday.entity.Holiday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** A college holiday (create or full replace). endDate is inclusive; null = one day. batchIds empty/null = everyone. */
public record HolidayRequest(
        @NotBlank(message = "name is required") @Size(max = 150, message = "name must be at most 150 characters") String name,
        @NotNull(message = "startDate is required") LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "category is required") Holiday.Category category,
        @Size(max = 500, message = "description must be at most 500 characters") String description,
        @Size(max = 20, message = "batchIds may list at most 20 cohorts") List<Long> batchIds
) {
}
