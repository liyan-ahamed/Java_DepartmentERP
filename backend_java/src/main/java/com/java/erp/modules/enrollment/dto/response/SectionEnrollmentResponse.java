package com.java.erp.modules.enrollment.dto.response;

/**
 * Result of enrolling a whole section: students already holding an enrollment row are left as they
 * are (a DROPPED or COMPLETED student is not re-enrolled automatically).
 */
public record SectionEnrollmentResponse(
        Long subjectOfferingId,
        Long sectionId,
        int activeStudentsInSection,
        int enrolled,
        int alreadyEnrolled,
        int skippedDropped,
        int skippedCompleted
) {
}
