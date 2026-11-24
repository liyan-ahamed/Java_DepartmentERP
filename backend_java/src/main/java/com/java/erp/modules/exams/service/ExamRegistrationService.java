package com.java.erp.modules.exams.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.exams.dto.request.ReviewRegistrationRequest;
import com.java.erp.modules.exams.dto.request.SaveRegistrationRequest;
import com.java.erp.modules.exams.dto.response.EligibleSubjectResponse;
import com.java.erp.modules.exams.dto.response.ExamPeriodResponse;
import com.java.erp.modules.exams.dto.response.ExamRegistrationResponse;
import com.java.erp.modules.exams.dto.response.StudentExamPeriodResponse;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.entity.ExamRegistration;
import com.java.erp.modules.exams.entity.ExamRegistrationSubject;
import com.java.erp.modules.exams.repository.ExamPeriodRepository;
import com.java.erp.modules.exams.repository.ExamRegistrationRepository;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.StudentNotifier;
import com.java.erp.modules.system.entity.Student;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Student exam registration. Eligible subjects = active offerings of the period's term (academic year
 * + semester) that the student is ENROLLED in. Students act only while the period is OPEN and today
 * is inside the registration window. HOD approves or rejects SUBMITTED registrations.
 *
 * DRAFT --submit--> SUBMITTED --approve--> APPROVED
 *                             --reject---> REJECTED --edit--> DRAFT
 */
@Service
public class ExamRegistrationService {

    static final String MODULE = "EXAMS";
    static final String ENTITY_TYPE = "ExamRegistration";

    private final ExamRegistrationRepository registrations;
    private final ExamPeriodRepository periods;
    private final ExamPeriodService periodService;
    private final SubjectOfferingRepository offerings;
    private final UserRepository users;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;
    private final StudentNotifier notifier;
    private final AuditRecorder audit;

    public ExamRegistrationService(ExamRegistrationRepository registrations, ExamPeriodRepository periods,
                                   ExamPeriodService periodService, SubjectOfferingRepository offerings,
                                   UserRepository users, EnrollmentRoster roster, CurrentStudentResolver currentStudent,
                                   StudentNotifier notifier, AuditRecorder audit) {
        this.registrations = registrations;
        this.periods = periods;
        this.periodService = periodService;
        this.offerings = offerings;
        this.users = users;
        this.roster = roster;
        this.currentStudent = currentStudent;
        this.notifier = notifier;
        this.audit = audit;
    }

    // ---- Student ----

    /** OPEN periods in which the student has at least one eligible subject. */
    @Transactional(readOnly = true)
    public List<StudentExamPeriodResponse> openPeriods() {
        Student student = currentStudent.require();
        List<StudentExamPeriodResponse> result = new ArrayList<>();
        for (ExamPeriod period : periods.findByStatusOrderByRegistrationEndAscIdAsc(ExamPeriod.Status.OPEN)) {
            int eligible = eligibleOfferings(student, period).size();
            if (eligible == 0) continue;
            Optional<ExamRegistration> reg = registrations.findByExamPeriodIdAndStudentId(period.getId(), student.getId());
            result.add(new StudentExamPeriodResponse(ExamPeriodResponse.from(period), eligible,
                    reg.map(ExamRegistration::getId).orElse(null), reg.map(ExamRegistration::getStatus).orElse(null)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<EligibleSubjectResponse> eligibleSubjects(Long periodId) {
        Student student = currentStudent.require();
        ExamPeriod period = periodService.require(periodId);
        if (period.getStatus() == ExamPeriod.Status.DRAFT) {
            throw new ResourceNotFoundException("ExamPeriod", "id", periodId);
        }
        Set<Long> selected = new HashSet<>();
        registrations.findByExamPeriodIdAndStudentId(periodId, student.getId())
                .ifPresent(r -> r.getSubjects().forEach(s -> selected.add(s.getSubjectOffering().getId())));
        return eligibleOfferings(student, period).values().stream()
                .map(o -> new EligibleSubjectResponse(o.getId(), o.getSubject().getId(), o.getSubject().getCode(),
                        o.getSubject().getName(), o.getSubject().getSubjectType(), o.getStaff().getName(),
                        selected.contains(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamRegistrationResponse> myRegistrations() {
        Student student = currentStudent.require();
        return registrations.findByStudentIdOrderByIdDesc(student.getId()).stream().map(ExamRegistrationResponse::from).toList();
    }

    /** Creates the student's DRAFT registration or replaces the subjects of a DRAFT / REJECTED one. */
    @Transactional
    public ExamRegistrationResponse saveMine(Long periodId, SaveRegistrationRequest request) {
        Student student = currentStudent.require();
        ExamPeriod period = requireWindowOpen(periodId);
        Map<Long, SubjectOffering> eligible = eligibleOfferings(student, period);
        List<String> errors = new ArrayList<>();
        Set<Long> wanted = new LinkedHashSet<>();
        for (Long id : request.subjectOfferingIds()) {
            if (!wanted.add(id)) errors.add("Subject offering " + id + " is listed more than once");
            else if (!eligible.containsKey(id)) errors.add("Subject offering " + id + " is not one of your eligible (enrolled) subjects for this exam period");
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Exam registration validation failed", errors);
        }

        ExamRegistration registration = registrations.findByExamPeriodIdAndStudentId(periodId, student.getId())
                .orElseGet(() -> new ExamRegistration(period, student));
        ExamRegistration.Status old = registration.getId() == null ? null : registration.getStatus();
        if (old == ExamRegistration.Status.SUBMITTED || old == ExamRegistration.Status.APPROVED) {
            throw new ConflictException("A " + old + " registration can no longer be changed");
        }
        // Replace the selection by difference (removing and re-adding the same subject would break the unique key).
        registration.getSubjects().removeIf(s -> !wanted.contains(s.getSubjectOffering().getId()));
        Set<Long> kept = new HashSet<>();
        registration.getSubjects().forEach(s -> kept.add(s.getSubjectOffering().getId()));
        for (Long id : wanted) {
            if (!kept.contains(id)) registration.getSubjects().add(new ExamRegistrationSubject(registration, eligible.get(id)));
        }
        registration.setStatus(ExamRegistration.Status.DRAFT);
        registration = registrations.saveAndFlush(registration);
        log("SAVE", registration, old);
        return ExamRegistrationResponse.from(registration);
    }

    /** DRAFT -> SUBMITTED inside the registration window; subjects are re-checked against current enrollments. */
    @Transactional
    public ExamRegistrationResponse submitMine(Long periodId) {
        Student student = currentStudent.require();
        ExamPeriod period = requireWindowOpen(periodId);
        ExamRegistration registration = registrations.findByExamPeriodIdAndStudentId(periodId, student.getId())
                .orElseThrow(() -> new BadRequestException("Choose your subjects before submitting"));
        if (registration.getStatus() != ExamRegistration.Status.DRAFT) {
            throw new ConflictException("Only a DRAFT registration can be submitted (it is " + registration.getStatus() + ")");
        }
        if (registration.getSubjects().isEmpty()) {
            throw new BadRequestException("Choose at least one subject before submitting");
        }
        Map<Long, SubjectOffering> eligible = eligibleOfferings(student, period);
        List<String> stale = registration.getSubjects().stream()
                .filter(s -> !eligible.containsKey(s.getSubjectOffering().getId()))
                .map(s -> s.getSubjectOffering().getSubject().getCode() + " is no longer an eligible subject").toList();
        if (!stale.isEmpty()) {
            throw new BadRequestException("Update your subjects before submitting", stale);
        }
        registration.setStatus(ExamRegistration.Status.SUBMITTED);
        registration.setSubmittedAt(LocalDateTime.now());
        registration.setReviewedBy(null);
        registration.setReviewedAt(null);
        registration.setReviewNote(null);
        registrations.saveAndFlush(registration);
        log("SUBMIT", registration, ExamRegistration.Status.DRAFT);
        return ExamRegistrationResponse.from(registration);
    }

    // ---- HOD ----

    @Transactional(readOnly = true)
    public PagedResponse<ExamRegistrationResponse> list(Long examPeriodId, ExamRegistration.Status status, Long sectionId,
                                                        Long studentId, int page, int size) {
        Specification<ExamRegistration> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (examPeriodId != null) p.add(cb.equal(root.get("examPeriod").get("id"), examPeriodId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (sectionId != null) p.add(cb.equal(root.get("student").get("section").get("id"), sectionId));
            if (studentId != null) p.add(cb.equal(root.get("student").get("id"), studentId));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<ExamRegistration> result = registrations.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("student.registerNumber"), Sort.Order.asc("id"))));
        return PagedResponse.of(result.getContent().stream().map(ExamRegistrationResponse::from).toList(), result);
    }

    /** HOD: any registration. STUDENT: own only (403 otherwise). */
    @Transactional(readOnly = true)
    public ExamRegistrationResponse get(Long id) {
        ExamRegistration registration = require(id);
        if (!SecurityUtils.hasAuthority("ROLE_HOD")
                && !registration.getStudent().getId().equals(currentStudent.require().getId())) {
            throw new ForbiddenException("You can only view your own exam registrations");
        }
        return ExamRegistrationResponse.from(registration);
    }

    @Transactional
    public ExamRegistrationResponse approve(Long id, ReviewRegistrationRequest request) {
        return decide(id, ExamRegistration.Status.APPROVED, request == null ? null : request.reviewNote());
    }

    @Transactional
    public ExamRegistrationResponse reject(Long id, ReviewRegistrationRequest request) {
        String note = request == null ? null : request.reviewNote();
        if (note == null || note.trim().length() < 5) {
            throw new BadRequestException("A rejection reason of at least 5 characters is required");
        }
        return decide(id, ExamRegistration.Status.REJECTED, note);
    }

    private ExamRegistrationResponse decide(Long id, ExamRegistration.Status decision, String note) {
        ExamRegistration registration = require(id);
        if (registration.getStatus() != ExamRegistration.Status.SUBMITTED) {
            throw new ConflictException("Only a SUBMITTED registration can be " + decision.name().toLowerCase(Locale.ROOT)
                    + " (it is " + registration.getStatus() + ")");
        }
        registration.setStatus(decision);
        registration.setReviewedBy(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        registration.setReviewedAt(LocalDateTime.now());
        registration.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        registrations.saveAndFlush(registration);
        log(decision == ExamRegistration.Status.APPROVED ? "APPROVE" : "REJECT", registration, ExamRegistration.Status.SUBMITTED);

        ExamPeriod period = registration.getExamPeriod();
        boolean approved = decision == ExamRegistration.Status.APPROVED;
        notifier.notifyStudent(registration.getStudent(), new StudentNotifier.Message(
                approved ? Notification.NotificationType.INFO : Notification.NotificationType.ACTION_REQUIRED,
                "Exam registration " + (approved ? "approved" : "rejected") + ": " + period.getName(),
                "Your registration for " + period.getName() + (approved ? " has been approved."
                        : " was rejected: " + registration.getReviewNote()),
                MODULE, registration.getId(), "EXAM_REGISTRATION",
                approved ? Notification.NotificationPriority.NORMAL : Notification.NotificationPriority.HIGH));
        return ExamRegistrationResponse.from(require(id));
    }

    // ---- helpers ----

    /** Active offerings of the period's term in which the student is ENROLLED, keyed by id. */
    Map<Long, SubjectOffering> eligibleOfferings(Student student, ExamPeriod period) {
        List<Long> enrolled = roster.offeringIds(student.getId(), EnrollmentRoster.CURRENT);
        Map<Long, SubjectOffering> result = new LinkedHashMap<>();
        if (enrolled.isEmpty()) return result;
        offerings.findByIdIn(enrolled).stream()
                .filter(SubjectOffering::isActive)
                .filter(o -> o.getAcademicYear().equals(period.getAcademicYear()) && o.getSemester().equals(period.getSemester()))
                .sorted(Comparator.comparing(o -> o.getSubject().getCode()))
                .forEach(o -> result.put(o.getId(), o));
        return result;
    }

    private ExamPeriod requireWindowOpen(Long periodId) {
        ExamPeriod period = periodService.require(periodId);
        if (period.getStatus() == ExamPeriod.Status.DRAFT) {
            throw new ResourceNotFoundException("ExamPeriod", "id", periodId);
        }
        if (!period.isRegistrationWindowOpen(LocalDate.now())) {
            throw new ConflictException("Registration for " + period.getName() + " is not open (status " + period.getStatus()
                    + ", window " + period.getRegistrationStart() + " to " + period.getRegistrationEnd() + ")");
        }
        return period;
    }

    private ExamRegistration require(Long id) {
        return registrations.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("ExamRegistration", "id", id));
    }

    private void log(String action, ExamRegistration r, ExamRegistration.Status oldStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("registrationId", r.getId());
        m.put("examPeriodId", r.getExamPeriod().getId());
        m.put("studentId", r.getStudent().getId());
        m.put("subjects", r.getSubjects().size());
        m.put("oldStatus", oldStatus);
        m.put("newStatus", r.getStatus());
        audit.record(action, MODULE, ENTITY_TYPE, r.getId(), m);
    }
}
