package com.java.erp.modules.attendance.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full roster of a session: every ENROLLED student, marked or not (status NOT_MARKED), plus any
 * student who was marked earlier but is no longer enrolled (inRoster = false).
 */
public record SessionRecordsResponse(
        AttendanceSessionResponse session,
        int rosterSize,
        int marked,
        int notMarked,
        List<Row> records
) {
    public record Row(
            Long studentId,
            String registerNumber,
            String studentName,
            boolean inRoster,
            Long recordId,
            String status,
            String remarks,
            LocalDateTime updatedAt
    ) {
    }
}
