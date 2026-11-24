package com.java.erp.modules.results.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.results.entity.ExamResult;
import com.java.erp.modules.system.entity.Student;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExamResultResponse(
        Long id,
        Long studentId,
        String registerNumber,
        String studentName,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String sectionName,
        String academicYear,
        Integer semester,
        Long examPeriodId,
        String examPeriodName,
        ExamPeriod.ExamType examType,
        BigDecimal marksObtained,
        BigDecimal maxMarks,
        String grade,
        ExamResult.ResultStatus resultStatus,
        String remarks,
        boolean published,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt
) {
    public static ExamResultResponse from(ExamResult r) {
        Student s = r.getStudent();
        SubjectOffering o = r.getSubjectOffering();
        ExamPeriod p = r.getExamPeriod();
        return new ExamResultResponse(r.getId(), s.getId(), s.getRegisterNumber(), s.getName(), o.getId(),
                o.getSubject().getId(), o.getSubject().getCode(), o.getSubject().getName(), o.getSection().getSectionName(),
                o.getAcademicYear(), o.getSemester(), p.getId(), p.getName(), p.getExamType(), r.getMarksObtained(),
                r.getMaxMarks(), r.getGrade(), r.getResultStatus(), r.getRemarks(), r.isPublished(), r.getPublishedAt(),
                r.getUpdatedAt());
    }
}
