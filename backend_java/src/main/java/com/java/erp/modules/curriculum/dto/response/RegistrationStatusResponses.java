package com.java.erp.modules.curriculum.dto.response;

import com.java.erp.modules.curriculum.service.RegistrationEvaluator;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;

import java.util.List;
import java.util.Map;

/** Response shapes of /course-enrollments/registration-status (see docs/ERP_API_CONTRACT.md, section C). */
public final class RegistrationStatusResponses {

    private RegistrationStatusResponses() {
    }

    /** One student of the list. The four compulsory / elective counts are null when no curriculum is configured. */
    public record StudentRow(
            Long studentId,
            String registerNumber,
            String studentName,
            Long batchId,
            String batchName,
            Integer semester,
            Long sectionId,
            String sectionName,
            int registeredCount,
            Integer compulsoryRegistered,
            Integer compulsoryRequired,
            Integer electiveGroupsSatisfied,
            Integer electiveGroupsRequired,
            RegistrationEvaluator.Status status
    ) {
    }

    public record Summary(
            long totalStudents,
            boolean curriculumConfigured,
            long offeredCourseCount,
            Map<RegistrationEvaluator.Status, Long> counts
    ) {
    }

    public record StudentInfo(Long studentId, String registerNumber, String studentName, Long batchId, String batchName,
                              Integer semester, Long sectionId, String sectionName) {
    }

    /**
     * A subject in the detail. With an enrollment row in the semester, offeringId / staffName / enrollmentId /
     * enrollmentStatus describe it (registered = it counts). Otherwise offeringId / staffName are the active offering of
     * the student's section, or null.
     */
    public record SubjectItem(Long subjectId, String code, String name, Long offeringId, String staffName,
                              boolean registered, Long enrollmentId, CourseEnrollment.Status enrollmentStatus) {
    }

    public record ElectiveGroupItem(Long groupId, String name, int requiredSelections, int selectedCount, boolean satisfied,
                                    List<SubjectItem> options) {
    }

    public record Detail(
            StudentInfo student,
            RegistrationEvaluator.Status status,
            boolean curriculumConfigured,
            int registeredCount,
            List<SubjectItem> compulsory,
            List<ElectiveGroupItem> electiveGroups,
            List<SubjectItem> otherRegistrations
    ) {
    }
}
