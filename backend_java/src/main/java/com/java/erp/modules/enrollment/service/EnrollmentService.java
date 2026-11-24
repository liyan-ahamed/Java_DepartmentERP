package com.java.erp.modules.enrollment.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.dto.request.EnrollStudentRequest;
import com.java.erp.modules.enrollment.dto.response.EnrollmentResponse;
import com.java.erp.modules.enrollment.dto.response.SectionEnrollmentResponse;
import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.enrollment.repository.CourseEnrollmentRepository;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.StudentRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Course enrollment. HOD manages enrollments (single student or a whole section); STAFF read the
 * enrollments of their own offerings; STUDENT reads their own. Students never self-enroll.
 */
@Service
public class EnrollmentService {

    static final String MODULE = "ENROLLMENT";
    static final String ENTITY_TYPE = "CourseEnrollment";

    private final CourseEnrollmentRepository enrollments;
    private final SubjectOfferingRepository offerings;
    private final StudentRepository students;
    private final UserRepository users;
    private final CurrentStudentResolver currentStudent;
    private final AuditRecorder audit;

    public EnrollmentService(CourseEnrollmentRepository enrollments, SubjectOfferingRepository offerings,
                             StudentRepository students, UserRepository users,
                             CurrentStudentResolver currentStudent, AuditRecorder audit) {
        this.enrollments = enrollments;
        this.offerings = offerings;
        this.students = students;
        this.users = users;
        this.currentStudent = currentStudent;
        this.audit = audit;
    }

    /** HOD sees every enrollment; STAFF only those of offerings assigned to them. */
    @Transactional(readOnly = true)
    public PagedResponse<EnrollmentResponse> list(Long subjectOfferingId, Long sectionId, Long studentId,
                                                  CourseEnrollment.Status status, int page, int size) {
        Long staffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        Specification<CourseEnrollment> spec = (root, query, cb) -> {
            Join<CourseEnrollment, SubjectOffering> o = root.join("subjectOffering");
            List<Predicate> p = new ArrayList<>();
            if (staffId != null) p.add(cb.equal(o.get("staff").get("id"), staffId));
            if (subjectOfferingId != null) p.add(cb.equal(o.get("id"), subjectOfferingId));
            if (sectionId != null) p.add(cb.equal(o.get("section").get("id"), sectionId));
            if (studentId != null) p.add(cb.equal(root.get("student").get("id"), studentId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<CourseEnrollment> result = enrollments.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("subjectOffering.id"), Sort.Order.asc("student.registerNumber"))));
        return PagedResponse.of(result.getContent().stream().map(EnrollmentResponse::from).toList(), result);
    }

    /** Every enrollment row (any status) of one offering: HOD, or the assigned STAFF member. */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> roster(Long offeringId) {
        OfferingAccess.requireView(requireOffering(offeringId));
        return enrollments.findBySubjectOfferingIdOrderByStudentRegisterNumberAsc(offeringId).stream()
                .map(EnrollmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> myEnrollments() {
        Student student = currentStudent.require();
        return enrollments.findByStudentIdOrderBySubjectOfferingAcademicYearDescSubjectOfferingSemesterDescIdAsc(student.getId())
                .stream().map(EnrollmentResponse::from).toList();
    }

    @Transactional
    public EnrollmentResponse enrollStudent(EnrollStudentRequest request) {
        SubjectOffering offering = requireActiveOffering(request.subjectOfferingId());
        Student student = students.findById(request.studentId())
                .orElseThrow(() -> new BadRequestException("Student " + request.studentId() + " does not exist"));
        if (!student.isActive()) {
            throw new BadRequestException("Student " + student.getRegisterNumber() + " is inactive");
        }
        if (!student.getBatch().getId().equals(offering.getBatch().getId())) {
            throw new BadRequestException("Student " + student.getRegisterNumber() + " is not in this offering's batch");
        }
        enrollments.findByStudentIdAndSubjectOfferingId(student.getId(), offering.getId()).ifPresent(existing -> {
            throw new ConflictException(switch (existing.getStatus()) {
                case ENROLLED -> "Student is already enrolled in this subject offering";
                case DROPPED -> "Student dropped this subject offering; reactivate the existing enrollment instead";
                case COMPLETED -> "Student has already completed this subject offering";
            });
        });

        CourseEnrollment enrollment = enrollments.saveAndFlush(new CourseEnrollment(student, offering, currentUser()));
        audit.record("ENROLL", MODULE, ENTITY_TYPE, enrollment.getId(), Map.of(
                "studentId", student.getId(), "subjectOfferingId", offering.getId(), "status", enrollment.getStatus()));
        return EnrollmentResponse.from(enrollments.findWithDetailsById(enrollment.getId()).orElseThrow());
    }

    /**
     * Enrolls every active student of the offering's section who has no enrollment row yet. Students
     * already ENROLLED are counted; DROPPED and COMPLETED students are left unchanged.
     */
    @Transactional
    public SectionEnrollmentResponse enrollSection(Long offeringId) {
        SubjectOffering offering = requireActiveOffering(offeringId);
        List<Student> sectionStudents = students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(offering.getSection().getId());
        Map<Long, CourseEnrollment> existing = enrollments.findBySubjectOfferingId(offeringId).stream()
                .collect(Collectors.toMap(e -> e.getStudent().getId(), Function.identity()));

        int already = 0, dropped = 0, completed = 0;
        List<CourseEnrollment> toSave = new ArrayList<>();
        var enrolledBy = currentUser();
        for (Student s : sectionStudents) {
            CourseEnrollment e = existing.get(s.getId());
            if (e == null) toSave.add(new CourseEnrollment(s, offering, enrolledBy));
            else if (e.getStatus() == CourseEnrollment.Status.ENROLLED) already++;
            else if (e.getStatus() == CourseEnrollment.Status.DROPPED) dropped++;
            else completed++;
        }
        enrollments.saveAll(toSave);
        enrollments.flush();

        SectionEnrollmentResponse response = new SectionEnrollmentResponse(offeringId, offering.getSection().getId(),
                sectionStudents.size(), toSave.size(), already, dropped, completed);
        audit.record("ENROLL_SECTION", MODULE, "SubjectOffering", offeringId, Map.of(
                "subjectOfferingId", offeringId, "sectionId", offering.getSection().getId(), "enrolled", toSave.size(),
                "alreadyEnrolled", already, "skippedDropped", dropped, "skippedCompleted", completed));
        return response;
    }

    /** ENROLLED -> DROPPED, DROPPED -> ENROLLED (offering must be active), ENROLLED -> COMPLETED. */
    @Transactional
    public EnrollmentResponse changeStatus(Long id, CourseEnrollment.Status target) {
        CourseEnrollment enrollment = enrollments.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", "id", id));
        CourseEnrollment.Status current = enrollment.getStatus();
        boolean allowed = switch (current) {
            case ENROLLED -> target == CourseEnrollment.Status.DROPPED || target == CourseEnrollment.Status.COMPLETED;
            case DROPPED -> target == CourseEnrollment.Status.ENROLLED;
            case COMPLETED -> false;
        };
        if (!allowed) {
            throw new ConflictException("Cannot change an enrollment from " + current + " to " + target);
        }
        if (target == CourseEnrollment.Status.ENROLLED) {
            if (!enrollment.getSubjectOffering().isActive()) {
                throw new BadRequestException("This subject offering is inactive");
            }
            if (!enrollment.getStudent().isActive()) {
                throw new BadRequestException("Student " + enrollment.getStudent().getRegisterNumber() + " is inactive");
            }
        }
        enrollment.setStatus(target);
        enrollments.saveAndFlush(enrollment);
        audit.record("STATUS_CHANGE", MODULE, ENTITY_TYPE, id, Map.of(
                "studentId", enrollment.getStudent().getId(), "subjectOfferingId", enrollment.getSubjectOffering().getId(),
                "oldStatus", current, "newStatus", target));
        return EnrollmentResponse.from(enrollment);
    }

    private SubjectOffering requireOffering(Long id) {
        return offerings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", id));
    }

    private SubjectOffering requireActiveOffering(Long id) {
        SubjectOffering offering = offerings.findWithDetailsById(id)
                .orElseThrow(() -> new BadRequestException("Subject offering " + id + " does not exist"));
        if (!offering.isActive()) {
            throw new BadRequestException("This subject offering is inactive");
        }
        return offering;
    }

    private com.java.erp.modules.auth.entity.User currentUser() {
        return users.getReferenceById(SecurityUtils.getCurrentUserId());
    }
}
