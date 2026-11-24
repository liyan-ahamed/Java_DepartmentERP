package com.java.erp.modules.attendance.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.attendance.entity.AttendanceSession;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceSessionResponse(
        Long id,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long sectionId,
        String sectionName,
        Long staffId,
        String staffName,
        LocalDate attendanceDate,
        Long timetableSlotId,
        Integer periodNumber,
        String topic,
        AttendanceSession.Status status,
        Long createdById,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finalizedAt
) {
    public static AttendanceSessionResponse from(AttendanceSession s) {
        SubjectOffering o = s.getSubjectOffering();
        return new AttendanceSessionResponse(s.getId(), o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                o.getSubject().getName(), o.getSection().getId(), o.getSection().getSectionName(), o.getStaff().getId(),
                o.getStaff().getName(), s.getAttendanceDate(),
                s.getTimetableSlot() == null ? null : s.getTimetableSlot().getId(), s.getPeriodNumber(), s.getTopic(),
                s.getStatus(), s.getCreatedBy().getId(), s.getCreatedAt(), s.getUpdatedAt(), s.getFinalizedAt());
    }
}
