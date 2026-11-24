package com.java.erp.modules.exams.dto.response;

import com.java.erp.modules.academic.entity.Subject;

/** A subject offering of the period's term the student is ENROLLED in; selected = in their registration. */
public record EligibleSubjectResponse(
        Long subjectOfferingId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Subject.SubjectType subjectType,
        String staffName,
        boolean selected
) {
}
