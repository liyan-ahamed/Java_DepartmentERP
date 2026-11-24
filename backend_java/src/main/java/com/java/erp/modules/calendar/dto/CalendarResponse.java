package com.java.erp.modules.calendar.dto;

import java.util.List;

public record CalendarResponse(List<CalendarItemResponse> entries, List<Integer> governmentYearsAvailable) {}
