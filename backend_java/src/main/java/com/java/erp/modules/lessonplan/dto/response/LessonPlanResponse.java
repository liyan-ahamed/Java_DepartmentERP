package com.java.erp.modules.lessonplan.dto.response;

import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.lessonplan.entity.LessonPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LessonPlanResponse(
        Long id,
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long sectionId,
        String sectionName,
        Long staffId,
        String staffName,
        Integer unitNumber,
        String topic,
        String description,
        LocalDate plannedDate,
        LocalDate completedDate,
        LessonPlan.Status status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LessonPlanResponse from(LessonPlan p) {
        SubjectOffering o = p.getSubjectOffering();
        return new LessonPlanResponse(p.getId(), o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                o.getSubject().getName(), o.getSection().getId(), o.getSection().getSectionName(), o.getStaff().getId(),
                o.getStaff().getName(), p.getUnitNumber(), p.getTopic(), p.getDescription(), p.getPlannedDate(),
                p.getCompletedDate(), p.getStatus(), p.getNotes(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
