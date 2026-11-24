package com.java.erp.modules.enrollment.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.system.entity.Student;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long studentId,
        String registerNumber,
        String studentName,
        Long studentSectionId,
        String studentSectionName,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long offeringSectionId,
        String offeringSectionName,
        String academicYear,
        Integer semester,
        Long staffId,
        String staffName,
        CourseEnrollment.Status status,
        LocalDateTime enrolledAt,
        LocalDateTime updatedAt,
        /** False when the subject offering has since been deactivated (e.g. retired demo courses). */
        boolean offeringActive,
        Integer credits
) {
    public static EnrollmentResponse from(CourseEnrollment e) {
        Student s = e.getStudent();
        SubjectOffering o = e.getSubjectOffering();
        return new EnrollmentResponse(e.getId(), s.getId(), s.getRegisterNumber(), s.getName(),
                s.getSection().getId(), s.getSection().getSectionName(),
                o.getId(), o.getSubject().getId(), o.getSubject().getCode(), o.getSubject().getName(),
                o.getSection().getId(), o.getSection().getSectionName(), o.getAcademicYear(), o.getSemester(),
                o.getStaff().getId(), o.getStaff().getName(), e.getStatus(), e.getEnrolledAt(), e.getUpdatedAt(),
                o.isActive(), o.getSubject().getCredits());
    }
}
