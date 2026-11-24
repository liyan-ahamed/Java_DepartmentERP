package com.java.erp.modules.attendance.dto.request;

import com.java.erp.modules.attendance.entity.StudentAttendance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Creates or updates many students' marks of one DRAFT session in one transaction (all or nothing). */
public record BulkAttendanceRequest(
        @NotEmpty @Size(max = 500) List<@NotNull @Valid Entry> records
) {
    public record Entry(
            @NotNull Long studentId,
            @NotNull StudentAttendance.AttendanceStatus status,
            @Size(max = 255) String remarks
    ) {
    }
}
