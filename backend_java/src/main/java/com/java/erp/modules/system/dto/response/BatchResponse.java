package com.java.erp.modules.system.dto.response;

import com.java.erp.modules.academic.service.AcademicCalendar;
import com.java.erp.modules.system.entity.Batch;

import java.time.LocalDate;

/**
 * currentSemester / currentAcademicYear / termStatus come from the institution's term calendar
 * ({@link AcademicCalendar}: admission year + today). currentSemester and currentAcademicYear are null when the
 * cohort has not started (NOT_STARTED) or has finished Semester 8 (COMPLETED).
 */
public record BatchResponse(
        Long id,
        String name,
        Integer admissionYear,
        Integer graduationYear,
        Short currentYear,
        boolean active,
        Integer currentSemester,
        String currentAcademicYear,
        AcademicCalendar.TermStatus termStatus
) {
    public static BatchResponse from(Batch b) {
        AcademicCalendar.Position position = AcademicCalendar.position(b.getAdmissionYear(), LocalDate.now());
        Integer semester = position.currentSemester();
        return new BatchResponse(b.getId(), b.getBatchName(), b.getAdmissionYear(), b.getGraduationYear(),
                b.getCurrentYear(), b.isActive(), semester,
                semester == null ? null : AcademicCalendar.academicYear(b.getAdmissionYear(), semester), position.status());
    }
}
