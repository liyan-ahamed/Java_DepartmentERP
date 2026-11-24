package com.java.erp.modules.academic.dto.response;

import com.java.erp.modules.academic.service.AcademicCalendar;

import java.time.LocalDate;
import java.util.List;

/**
 * One cohort in the academic selector (batch -> semester -> class) with the semesters in which the user has
 * subject offerings. currentSemester / termStatus come from {@link AcademicCalendar} (admission year + date).
 * Semester status: CURRENT, PREVIOUS (before the current one, or every semester of a COMPLETED cohort),
 * UPCOMING (after the current one, or every semester of a NOT_STARTED cohort).
 */
public record TeachingTermsResponse(
        Long batchId,
        String batchName,
        boolean batchActive,
        Integer admissionYear,
        Integer yearOfStudy,
        Integer currentSemester,
        AcademicCalendar.TermStatus termStatus,
        LocalDate currentTermStart,
        LocalDate currentTermEnd,
        List<Semester> semesters
) {
    public enum SemesterStatus { CURRENT, PREVIOUS, UPCOMING }

    public record Semester(
            int semester,
            SemesterStatus status,
            LocalDate startDate,
            LocalDate endDate,
            List<String> academicYears,
            int offeringCount,
            int activeOfferingCount
    ) {
    }
}
