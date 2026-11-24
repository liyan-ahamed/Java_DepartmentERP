package com.java.erp.modules.exams.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.entity.ExamRegistration;
import com.java.erp.modules.system.entity.Student;

import java.time.LocalDateTime;
import java.util.List;

public record ExamRegistrationResponse(
        Long id,
        Long examPeriodId,
        String examPeriodName,
        ExamPeriod.ExamType examType,
        Long studentId,
        String registerNumber,
        String studentName,
        String sectionName,
        ExamRegistration.Status status,
        LocalDateTime submittedAt,
        Long reviewedById,
        String reviewedByName,
        LocalDateTime reviewedAt,
        String reviewNote,
        List<Subject> subjects,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Subject(Long subjectOfferingId, Long subjectId, String subjectCode, String subjectName) {
    }

    public static ExamRegistrationResponse from(ExamRegistration r) {
        Student s = r.getStudent();
        ExamPeriod p = r.getExamPeriod();
        List<Subject> subjects = r.getSubjects().stream().map(rs -> {
            SubjectOffering o = rs.getSubjectOffering();
            return new Subject(o.getId(), o.getSubject().getId(), o.getSubject().getCode(), o.getSubject().getName());
        }).toList();
        return new ExamRegistrationResponse(r.getId(), p.getId(), p.getName(), p.getExamType(), s.getId(),
                s.getRegisterNumber(), s.getName(), s.getSection().getSectionName(), r.getStatus(), r.getSubmittedAt(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getId(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getName(), r.getReviewedAt(), r.getReviewNote(),
                subjects, r.getCreatedAt(), r.getUpdatedAt());
    }
}
