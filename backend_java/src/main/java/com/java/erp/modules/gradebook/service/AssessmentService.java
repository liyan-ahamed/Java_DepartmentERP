package com.java.erp.modules.gradebook.service;

import com.java.erp.common.audit.Auditable;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.auth.entity.User;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.gradebook.dto.request.CreateAssessmentRequest;
import com.java.erp.modules.gradebook.dto.request.MarksEntryDeadlineRequest;
import com.java.erp.modules.gradebook.dto.request.UpdateAssessmentRequest;
import com.java.erp.modules.gradebook.dto.response.AssessmentResponse;
import com.java.erp.modules.gradebook.entity.Assessment;
import com.java.erp.modules.gradebook.entity.StudentGrade;
import com.java.erp.modules.gradebook.repository.AssessmentRepository;
import com.java.erp.modules.gradebook.repository.StudentGradeRepository;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.NotificationService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Assessment management and lifecycle (DRAFT -> FINALIZED).
 * Only the staff member assigned to the offering may change assessments; HOD is read-only.
 */
@Service
public class AssessmentService {

    static final String NOTIFICATION_MODULE = "GRADEBOOK";
    static final String NOTIFICATION_REFERENCE_TYPE = "ASSESSMENT";

    private final AssessmentRepository assessments;
    private final SubjectOfferingRepository offerings;
    private final StudentGradeRepository grades;
    private final StudentRepository students;
    private final UserRepository users;
    private final NotificationService notifications;
    private final InternalMarksService internalMarks;
    private final AuditRecorder audit;

    public AssessmentService(AssessmentRepository assessments, SubjectOfferingRepository offerings,
                             StudentGradeRepository grades, StudentRepository students, UserRepository users,
                             NotificationService notifications, InternalMarksService internalMarks, AuditRecorder audit) {
        this.audit = audit;
        this.assessments = assessments;
        this.offerings = offerings;
        this.grades = grades;
        this.students = students;
        this.users = users;
        this.notifications = notifications;
        this.internalMarks = internalMarks;
    }

    @Transactional
    @Auditable(action = "CREATE", module = "GRADEBOOK", entityType = "Assessment")
    public AssessmentResponse create(CreateAssessmentRequest request) {
        SubjectOffering offering = offerings.findWithDetailsById(request.subjectOfferingId())
                .orElseThrow(() -> new BadRequestException("Subject offering " + request.subjectOfferingId() + " does not exist"));
        OfferingAccess.requireManage(offering);
        validatePassMarks(request.maxMarks(), request.passMarks());

        Assessment assessment = new Assessment();
        assessment.setSubjectOffering(offering);
        assessment.setCreatedBy(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        assessment.setTitle(request.title().trim());
        assessment.setAssessmentType(request.assessmentType());
        assessment.setMaxMarks(request.maxMarks());
        assessment.setPassMarks(request.passMarks());
        assessment.setWeightage(request.weightage());
        assessment.setAssessmentDate(request.assessmentDate());
        assessment = assessments.saveAndFlush(assessment);
        return AssessmentResponse.from(requireAssessment(assessment.getId()));
    }

    @Transactional
    @Auditable(action = "UPDATE", module = "GRADEBOOK", entityType = "Assessment")
    public AssessmentResponse update(Long id, UpdateAssessmentRequest request) {
        Assessment assessment = requireAssessment(id);
        OfferingAccess.requireManage(assessment.getSubjectOffering());
        requireDraft(assessment);
        validatePassMarks(request.maxMarks(), request.passMarks());
        BigDecimal highest = grades.findHighestMarks(id);
        if (highest != null && highest.compareTo(request.maxMarks()) > 0) {
            throw new BadRequestException("Max marks cannot be lower than marks already entered (highest is " + highest.stripTrailingZeros().toPlainString() + ")");
        }

        assessment.setTitle(request.title().trim());
        assessment.setAssessmentType(request.assessmentType());
        assessment.setMaxMarks(request.maxMarks());
        assessment.setPassMarks(request.passMarks());
        assessment.setWeightage(request.weightage());
        assessment.setAssessmentDate(request.assessmentDate());
        return AssessmentResponse.from(assessments.saveAndFlush(assessment));
    }

    /** Deletes a DRAFT assessment and its grades (student_grades rows cascade in the database). */
    @Transactional
    @Auditable(action = "DELETE", module = "GRADEBOOK", entityType = "Assessment")
    public void delete(Long id) {
        Assessment assessment = requireAssessment(id);
        OfferingAccess.requireManage(assessment.getSubjectOffering());
        requireDraft(assessment);
        assessments.delete(assessment);
        assessments.flush();
    }

    /**
     * Publishes the assessment: grades become visible to students, and each student of the
     * class with a login gets one in-app notification. The DRAFT -> FINALIZED change is a single
     * guarded update, so a retried or concurrent request gets 409 and sends nothing.
     * Refused (400, one error per student) while any student of the class list has neither a valid mark nor an
     * explicit ABSENT / EXEMPTED status.
     */
    @Transactional
    @Auditable(action = "FINALIZE", module = "GRADEBOOK", entityType = "Assessment")
    public AssessmentResponse finalizeAssessment(Long id) {
        Assessment assessment = requireAssessment(id);
        OfferingAccess.requireManage(assessment.getSubjectOffering());
        requireDraft(assessment);
        requireCompleteMarks(assessment);
        if (assessments.finalizeIfDraft(id, LocalDateTime.now(), Assessment.Status.DRAFT, Assessment.Status.FINALIZED) == 0) {
            throw new ConflictException("This assessment is already finalized");
        }

        Assessment finalized = requireAssessment(id);
        internalMarks.ensureRecords(finalized.getSubjectOffering());
        notifyStudents(finalized);
        return AssessmentResponse.from(finalized);
    }

    /**
     * HOD: sets (or clears with null) the marks-entry deadline. Only the deadline changes; the HOD still cannot
     * edit the assessment or its marks. Not possible once the assessment is finalized.
     */
    @Transactional
    public AssessmentResponse setMarksEntryDeadline(Long id, MarksEntryDeadlineRequest request) {
        Assessment assessment = requireAssessment(id);
        if (assessment.getStatus() == Assessment.Status.FINALIZED) {
            throw new ConflictException("Marks entry is closed: this assessment is finalized");
        }
        OffsetDateTime old = assessment.getMarksEntryDueAt();
        assessment.setMarksEntryDueAt(request.marksEntryDueAt());
        assessments.saveAndFlush(assessment);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("assessmentId", id);
        summary.put("subjectOfferingId", assessment.getSubjectOffering().getId());
        summary.put("oldDueAt", old == null ? null : old.toString());
        summary.put("newDueAt", request.marksEntryDueAt() == null ? null : request.marksEntryDueAt().toString());
        audit.record("MARKS_ENTRY_DEADLINE_UPDATED", "GRADEBOOK", "Assessment", id, summary);
        return AssessmentResponse.from(assessment);
    }

    @Transactional(readOnly = true)
    public AssessmentResponse get(Long id) {
        Assessment assessment = requireAssessment(id);
        OfferingAccess.requireView(assessment.getSubjectOffering());
        return AssessmentResponse.from(assessment);
    }

    /** HOD sees all assessments; STAFF only those of offerings assigned to them. */
    @Transactional(readOnly = true)
    public PagedResponse<AssessmentResponse> list(Long subjectOfferingId, Long subjectId, Long batchId, Long sectionId,
                                                  String academicYear, Integer semester,
                                                  Assessment.AssessmentType type, Assessment.Status status,
                                                  int page, int size) {
        Long staffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        Specification<Assessment> spec = (root, query, cb) -> {
            Join<Assessment, SubjectOffering> o = root.join("subjectOffering");
            List<Predicate> p = new ArrayList<>();
            if (staffId != null) p.add(cb.equal(o.get("staff").get("id"), staffId));
            if (subjectOfferingId != null) p.add(cb.equal(o.get("id"), subjectOfferingId));
            if (subjectId != null) p.add(cb.equal(o.get("subject").get("id"), subjectId));
            if (batchId != null) p.add(cb.equal(o.get("batch").get("id"), batchId));
            if (sectionId != null) p.add(cb.equal(o.get("section").get("id"), sectionId));
            if (academicYear != null) p.add(cb.equal(o.get("academicYear"), academicYear.trim()));
            if (semester != null) p.add(cb.equal(o.get("semester"), semester));
            if (type != null) p.add(cb.equal(root.get("assessmentType"), type));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Assessment> result = assessments.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("assessmentDate"), Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream().map(AssessmentResponse::from).toList(), result);
    }

    Assessment requireAssessment(Long id) {
        return assessments.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment", "id", id));
    }

    static void requireDraft(Assessment assessment) {
        if (!assessment.isDraft()) {
            throw new ConflictException("This assessment is finalized and can no longer be changed");
        }
    }

    /** Every active student of the section needs a valid GRADED mark or an explicit ABSENT / EXEMPTED row. */
    private void requireCompleteMarks(Assessment assessment) {
        Map<Long, StudentGrade> byStudent = new HashMap<>();
        grades.findByAssessmentId(assessment.getId()).forEach(g -> byStudent.put(g.getStudent().getId(), g));
        List<String> errors = new ArrayList<>();
        int missing = 0;
        for (Student s : students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(
                assessment.getSubjectOffering().getSection().getId())) {
            StudentGrade g = byStudent.get(s.getId());
            if (g == null) {
                missing++;
                errors.add(s.getRegisterNumber() + " (studentId " + s.getId() + "): no marks and not marked absent");
            } else {
                GradeRules.problem(g.getGradeStatus(), g.getMarks(), assessment.getMaxMarks()).ifPresent(problem ->
                        errors.add(s.getRegisterNumber() + " (studentId " + s.getId() + "): " + problem));
            }
        }
        if (!errors.isEmpty()) {
            int invalid = errors.size() - missing;
            throw new BadRequestException("Cannot finalize: " + missing + " student(s) without marks or absent status, "
                    + invalid + " invalid mark(s)", errors);
        }
    }

    private static void validatePassMarks(BigDecimal maxMarks, BigDecimal passMarks) {
        if (passMarks != null && passMarks.compareTo(maxMarks) > 0) {
            throw new BadRequestException("Pass marks cannot exceed max marks");
        }
    }

    /**
     * One notification per student login of the class: students linked by students.user_id,
     * plus unlinked students whose email matches an active STUDENT login (same rule as
     * StudentRepository.findLinkedStudent).
     */
    private void notifyStudents(Assessment assessment) {
        SubjectOffering offering = assessment.getSubjectOffering();
        Set<Long> recipients = new LinkedHashSet<>();
        Set<String> unlinkedEmails = new HashSet<>();
        for (Student student : students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(offering.getSection().getId())) {
            User user = student.getUser();
            if (user != null) {
                if (user.isActive()) recipients.add(user.getId());
            } else if (student.getEmail() != null && !student.getEmail().isBlank()) {
                unlinkedEmails.add(student.getEmail().trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!unlinkedEmails.isEmpty()) {
            users.findActiveStudentUsersByEmails(unlinkedEmails).forEach(u -> recipients.add(u.getId()));
        }

        String subjectName = offering.getSubject().getName();
        String title = "Marks published: " + offering.getSubject().getCode();
        String message = assessment.getTitle() + " marks for " + subjectName + " have been published.";
        for (Long recipientId : recipients) {
            notifications.send(recipientId, Notification.NotificationType.INFO, title, message,
                    NOTIFICATION_MODULE, assessment.getId(), NOTIFICATION_REFERENCE_TYPE,
                    Notification.NotificationPriority.NORMAL);
        }
    }
}
