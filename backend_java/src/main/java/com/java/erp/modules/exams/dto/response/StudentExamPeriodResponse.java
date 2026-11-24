package com.java.erp.modules.exams.dto.response;

import com.java.erp.modules.exams.entity.ExamRegistration;

/** An OPEN exam period the student has eligible subjects in, with their registration status (null = none yet). */
public record StudentExamPeriodResponse(
        ExamPeriodResponse period,
        int eligibleSubjects,
        Long registrationId,
        ExamRegistration.Status registrationStatus
) {
}
