package com.java.erp.modules.attendance.dto.response;

public record BulkAttendanceResponse(
        Long sessionId,
        int received,
        int created,
        int updated,
        int unchanged,
        int rosterSize,
        int notMarked
) {
}
