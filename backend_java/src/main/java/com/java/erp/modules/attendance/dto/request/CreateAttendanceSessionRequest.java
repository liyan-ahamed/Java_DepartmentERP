package com.java.erp.modules.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** timetableSlotId is optional; when given, the period number defaults to the slot's. */
public record CreateAttendanceSessionRequest(
        @NotNull Long subjectOfferingId,
        @NotNull LocalDate attendanceDate,
        Long timetableSlotId,
        @Positive Integer periodNumber,
        @Size(max = 255) String topic
) {
}
