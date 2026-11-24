package com.java.erp.modules.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** DRAFT sessions only. The subject offering of a session cannot change. */
public record UpdateAttendanceSessionRequest(
        @NotNull LocalDate attendanceDate,
        Long timetableSlotId,
        @Positive Integer periodNumber,
        @Size(max = 255) String topic
) {
}
