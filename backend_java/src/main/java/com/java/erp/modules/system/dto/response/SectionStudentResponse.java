package com.java.erp.modules.system.dto.response;

import com.java.erp.modules.system.entity.Student;

/** One student of a section's roster. userId is null when the student has no ERP login. */
public record SectionStudentResponse(
        Long studentId,
        String registerNumber,
        String name,
        Long batchId,
        String batchName,
        Long sectionId,
        String sectionName,
        boolean active,
        Long userId
) {
    public static SectionStudentResponse from(Student s) {
        return new SectionStudentResponse(s.getId(), s.getRegisterNumber(), s.getName(), s.getBatch().getId(),
                s.getBatch().getBatchName(), s.getSection().getId(), s.getSection().getSectionName(), s.isActive(),
                s.getUser() == null ? null : s.getUser().getId());
    }
}
