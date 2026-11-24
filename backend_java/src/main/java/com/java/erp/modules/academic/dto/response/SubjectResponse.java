package com.java.erp.modules.academic.dto.response;

import com.java.erp.modules.academic.entity.Subject;

import java.time.LocalDateTime;
import java.util.List;

public record SubjectResponse(
        Long id,
        String code,
        String name,
        Subject.SubjectType subjectType,
        Integer credits,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Distinct (cohort, semester) of the subject's active offerings, by semester then cohort name. */
        List<OfferedIn> offeredIn
) {
    public record OfferedIn(Long batchId, String batchName, Integer semester) {
    }

    public static SubjectResponse from(Subject s, List<OfferedIn> offeredIn) {
        return new SubjectResponse(s.getId(), s.getCode(), s.getName(), s.getSubjectType(), s.getCredits(), s.isActive(),
                s.getCreatedAt(), s.getUpdatedAt(), offeredIn);
    }
}
