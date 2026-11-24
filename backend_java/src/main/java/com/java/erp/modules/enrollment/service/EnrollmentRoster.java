package com.java.erp.modules.enrollment.service;

import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.system.entity.Student;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only enrollment lookups shared by attendance, timetable, lesson plans, exams, results and
 * feedback. The class list of a subject offering is its ENROLLED active students.
 */
@Component
public class EnrollmentRoster {

    /** Enrollments that give a student access to an offering's data (current or finished). */
    public static final Set<CourseEnrollment.Status> CURRENT_OR_COMPLETED =
            EnumSet.of(CourseEnrollment.Status.ENROLLED, CourseEnrollment.Status.COMPLETED);
    public static final Set<CourseEnrollment.Status> CURRENT = EnumSet.of(CourseEnrollment.Status.ENROLLED);

    private final CourseEnrollmentRepository enrollments;

    public EnrollmentRoster(CourseEnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    /** Active ENROLLED students of the offering keyed by id, in register-number order. */
    @Transactional(readOnly = true)
    public Map<Long, Student> classList(Long offeringId) {
        Map<Long, Student> result = new LinkedHashMap<>();
        enrollments.findEnrolledStudents(offeringId).forEach(s -> result.put(s.getId(), s));
        return result;
    }

    /** Active students of the offering with one of the given statuses, keyed by id, in register-number order. */
    @Transactional(readOnly = true)
    public Map<Long, Student> students(Long offeringId, Set<CourseEnrollment.Status> statuses) {
        Map<Long, Student> result = new LinkedHashMap<>();
        enrollments.findStudentsByStatusIn(offeringId, statuses).forEach(s -> result.put(s.getId(), s));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(Long studentId, Long offeringId, Set<CourseEnrollment.Status> statuses) {
        return enrollments.existsByStudentIdAndSubjectOfferingIdAndStatusIn(studentId, offeringId, statuses);
    }

    @Transactional(readOnly = true)
    public List<Long> offeringIds(Long studentId, Set<CourseEnrollment.Status> statuses) {
        return enrollments.findOfferingIdsByStudentIdAndStatusIn(studentId, statuses);
    }
}
