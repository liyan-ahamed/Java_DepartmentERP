package com.java.erp.modules.academic.dto.response;

import com.java.erp.modules.academic.entity.Subject;
import com.java.erp.modules.academic.entity.SubjectOffering;

import java.time.LocalDateTime;

public record SubjectOfferingResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Subject.SubjectType subjectType,
        Integer credits,
        Long batchId,
        String batchName,
        Long sectionId,
        String sectionName,
        Long staffId,
        String staffName,
        String academicYear,
        Integer semester,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SubjectOfferingResponse from(SubjectOffering o) {
        Subject s = o.getSubject();
        return new SubjectOfferingResponse(o.getId(), s.getId(), s.getCode(), s.getName(), s.getSubjectType(), s.getCredits(),
                o.getBatch().getId(), o.getBatch().getBatchName(), o.getSection().getId(), o.getSection().getSectionName(),
                o.getStaff().getId(), o.getStaff().getName(), o.getAcademicYear(), o.getSemester(), o.isActive(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
