package com.java.erp.modules.curriculum.dto.response;

import com.java.erp.modules.academic.entity.Subject;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Curriculum of one cohort + semester. configured=false: nothing saved (lists empty; version, updatedAt, updatedBy null).
 */
public record CurriculumResponse(
        Long batchId,
        String batchName,
        Integer semester,
        boolean configured,
        Long version,
        LocalDateTime updatedAt,
        String updatedBy,
        List<SubjectItem> compulsorySubjects,
        List<ElectiveGroupItem> electiveGroups
) {
    public record SubjectItem(Long subjectId, String code, String name, Subject.SubjectType subjectType, Integer credits) {
        public static SubjectItem from(Subject s) {
            return new SubjectItem(s.getId(), s.getCode(), s.getName(), s.getSubjectType(), s.getCredits());
        }
    }

    public record ElectiveGroupItem(Long id, String name, int requiredSelections, int displayOrder, List<SubjectItem> options) {
    }
}
