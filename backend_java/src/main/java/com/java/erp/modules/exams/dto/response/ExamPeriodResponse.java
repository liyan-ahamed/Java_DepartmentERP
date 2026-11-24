package com.java.erp.modules.exams.dto.response;

import com.java.erp.modules.exams.entity.ExamPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExamPeriodResponse(
        Long id,
        String name,
        String academicYear,
        Integer semester,
        ExamPeriod.ExamType examType,
        LocalDate registrationStart,
        LocalDate registrationEnd,
        ExamPeriod.Status status,
        boolean registrationWindowOpen,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ExamPeriodResponse from(ExamPeriod p) {
        return new ExamPeriodResponse(p.getId(), p.getName(), p.getAcademicYear(), p.getSemester(), p.getExamType(),
                p.getRegistrationStart(), p.getRegistrationEnd(), p.getStatus(),
                p.isRegistrationWindowOpen(LocalDate.now()), p.getCreatedAt(), p.getUpdatedAt());
    }
}
