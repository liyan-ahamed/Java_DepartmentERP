package com.java.erp.modules.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CalendarEntryRequest(@NotNull LocalDate date,
                                   @NotBlank @Size(max = 255) String title,
                                   @NotNull Boolean holiday) {}
