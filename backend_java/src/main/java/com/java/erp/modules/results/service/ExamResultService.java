package com.java.erp.modules.results.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ForbiddenException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.repository.ExamPeriodRepository;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.StudentNotifier;
import com.java.erp.modules.results.dto.request.BulkResultRequest;
import com.java.erp.modules.results.dto.response.ExamResultResponse;
import com.java.erp.modules.results.dto.response.ResultBatchResponse;
import com.java.erp.modules.results.dto.response.ResultSheetResponse;
import com.java.erp.modules.results.entity.ExamResult;
import com.java.erp.modules.results.repository.ExamResultRepository;
import com.java.erp.modules.system.entity.Student;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Formal exam results. Who enters unpublished results depends on the exam period's type:
 *  - INTERNAL: the STAFF member assigned to the (active) offering.
 *  - END_SEMESTER and SUPPLEMENTARY: the COE (Controller of Examinations) only. They are read-only for STAFF.
 * HOD reads everything and publishes (no HOD editing, same convention as the Gradebook); COE reads everything;
 * STUDENT sees published results only. The offering's term must match the exam period's term, and each student
 * must be enrolled (ENROLLED or COMPLETED) in the offering. No GPA/CGPA is calculated.
 */
@Service
public class ExamResultService {

    static final String MODULE = "RESULTS";
    static final String ENTITY_TYPE = "ExamResult";
    static final String NOT_ENTERED = "NOT_ENTERED";

    private final ExamResultRepository results;
    private final SubjectOfferingRepository offerings;
    private final ExamPeriodRepository periods;
    private final UserRepository users;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;
    private final StudentNotifier notifier;
    private final AuditRecorder audit;

    public ExamResultService(ExamResultRepository results, SubjectOfferingRepository offerings, ExamPeriodRepository periods,
                             UserRepository users, EnrollmentRoster roster, CurrentStudentResolver currentStudent,
                             StudentNotifier notifier, AuditRecorder audit) {
        this.results = results;
        this.offerings = offerings;
        this.periods = periods;
        this.users = users;
        this.roster = roster;
        this.currentStudent = currentStudent;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ResultSheetResponse sheet(Long offeringId, Long periodId) {
        SubjectOffering offering = requireOffering(offeringId);
        if (!isCoe()) {
            OfferingAccess.requireView(offering);
        }
        ExamPeriod period = requirePeriod(periodId, offering);

        Map<Long, Student> students = roster.students(offeringId, EnrollmentRoster.CURRENT_OR_COMPLETED);
        Map<Long, ExamResult> byStudent = byStudent(offeringId, periodId);
        List<ResultSheetResponse.Row> rows = new ArrayList<>();
        students.values().forEach(s -> rows.add(row(s, true, byStudent.get(s.getId()))));
        byStudent.values().stream().filter(r -> !students.containsKey(r.getStudent().getId()))
                .forEach(r -> rows.add(row(r.getStudent(), false, r)));
        int published = (int) byStudent.values().stream().filter(ExamResult::isPublished).count();
        return new ResultSheetResponse(offeringId, offering.getSubject().getCode(), offering.getSubject().getName(),
                offering.getSection().getSectionName(), periodId, period.getName(), students.size(), byStudent.size(),
                published, rows);
    }

    /** All rows are validated first; any problem (including an already published row) rejects the request. */
    @Transactional
    public ResultBatchResponse saveSheet(Long offeringId, Long periodId, BulkResultRequest request) {
        SubjectOffering offering = requireOffering(offeringId);
        ExamPeriod period = requirePeriod(periodId, offering);
        requireResultEntry(offering, period);
        if (period.getStatus() == ExamPeriod.Status.DRAFT) {
            throw new ConflictException("Results cannot be entered for a DRAFT exam period");
        }

        Map<Long, Student> students = roster.students(offeringId, EnrollmentRoster.CURRENT_OR_COMPLETED);
        Map<Long, ExamResult> existing = byStudent(offeringId, periodId);
        List<String> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.results().size(); i++) {
            BulkResultRequest.Entry e = request.results().get(i);
            String row = "results[" + i + "] (studentId " + e.studentId() + "): ";
            if (!seen.add(e.studentId())) errors.add(row + "student appears more than once");
            else if (!students.containsKey(e.studentId())) errors.add(row + "student is not enrolled in this subject offering");
            else if (existing.containsKey(e.studentId()) && existing.get(e.studentId()).isPublished()) {
                errors.add(row + "result is already published and cannot be changed");
            }
            validate(e).ifPresent(problem -> errors.add(row + problem));
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Result validation failed", errors);
        }

        var enteredBy = users.getReferenceById(SecurityUtils.getCurrentUserId());
        int created = 0, updated = 0, unchanged = 0;
        List<ExamResult> toSave = new ArrayList<>();
        for (BulkResultRequest.Entry e : request.results()) {
            String grade = e.grade() == null || e.grade().isBlank() ? null : e.grade().trim().toUpperCase(Locale.ROOT);
            String remarks = e.remarks() == null || e.remarks().isBlank() ? null : e.remarks().trim();
            ExamResult r = existing.get(e.studentId());
            if (r == null) {
                r = new ExamResult(students.get(e.studentId()), offering, period);
                created++;
            } else if (same(r, e, grade, remarks)) {
                unchanged++;
                continue;
            } else {
                updated++;
            }
            r.setMarksObtained(e.marksObtained());
            r.setMaxMarks(e.maxMarks());
            r.setGrade(grade);
            r.setResultStatus(e.resultStatus());
            r.setRemarks(remarks);
            r.setEnteredBy(enteredBy);
            toSave.add(r);
        }
        results.saveAll(toSave);
        results.flush();
        audit.record("BULK_SAVE", MODULE, "SubjectOffering", offeringId, Map.of(
                "subjectOfferingId", offeringId, "examPeriodId", periodId, "received", request.results().size(),
                "created", created, "updated", updated));
        return new ResultBatchResponse(offeringId, periodId, request.results().size(), created, updated, unchanged, 0);
    }

    /** HOD publishes every unpublished result of the offering for the period; students are notified once. */
    @Transactional
    public ResultBatchResponse publish(Long offeringId, Long periodId) {
        SubjectOffering offering = requireOffering(offeringId);
        ExamPeriod period = requirePeriod(periodId, offering);
        List<Student> toNotify = byStudent(offeringId, periodId).values().stream()
                .filter(r -> !r.isPublished()).map(ExamResult::getStudent).toList();
        if (toNotify.isEmpty()) {
            throw new ConflictException("There are no unpublished results for this subject offering and exam period");
        }
        // Notify before the bulk update: it clears the persistence context (same transaction, so a failed
        // publish rolls the notifications back too).
        String code = offering.getSubject().getCode();
        int notified = notifier.notifyAll(toNotify, new StudentNotifier.Message(Notification.NotificationType.INFO,
                "Result published: " + code, "Your " + period.getName() + " result for " + code + " "
                + offering.getSubject().getName() + " has been published.", MODULE, period.getId(), "EXAM_RESULT",
                Notification.NotificationPriority.NORMAL));
        int published = results.publish(offeringId, periodId, users.getReferenceById(SecurityUtils.getCurrentUserId()),
                LocalDateTime.now());
        audit.record("PUBLISH", MODULE, "SubjectOffering", offeringId, Map.of(
                "subjectOfferingId", offeringId, "examPeriodId", periodId, "published", published, "notified", notified));
        return new ResultBatchResponse(offeringId, periodId, 0, 0, 0, 0, published);
    }

    /** HOD and COE: all results; STAFF: results of their own offerings. */
    @Transactional(readOnly = true)
    public PagedResponse<ExamResultResponse> list(Long examPeriodId, Long subjectOfferingId, Long sectionId, Long studentId,
                                                  Boolean published, ExamResult.ResultStatus status, int page, int size) {
        Long staffId = OfferingAccess.isHod() || isCoe() ? null : SecurityUtils.getCurrentUserId();
        Specification<ExamResult> spec = (root, query, cb) -> {
            Join<ExamResult, SubjectOffering> o = root.join("subjectOffering");
            List<Predicate> p = new ArrayList<>();
            if (staffId != null) p.add(cb.equal(o.get("staff").get("id"), staffId));
            if (examPeriodId != null) p.add(cb.equal(root.get("examPeriod").get("id"), examPeriodId));
            if (subjectOfferingId != null) p.add(cb.equal(o.get("id"), subjectOfferingId));
            if (sectionId != null) p.add(cb.equal(o.get("section").get("id"), sectionId));
            if (studentId != null) p.add(cb.equal(root.get("student").get("id"), studentId));
            if (published != null) p.add(cb.equal(root.get("published"), published));
            if (status != null) p.add(cb.equal(root.get("resultStatus"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<ExamResult> result = results.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("examPeriod.id"), Sort.Order.asc("id"))));
        return PagedResponse.of(result.getContent().stream().map(ExamResultResponse::from).toList(), result);
    }

    /** STUDENT: published results only. */
    @Transactional(readOnly = true)
    public List<ExamResultResponse> myResults(Long examPeriodId) {
        Student student = currentStudent.require();
        return results.findByStudentIdAndPublishedTrueOrderByExamPeriodIdDescSubjectOfferingIdAsc(student.getId()).stream()
                .filter(r -> examPeriodId == null || r.getExamPeriod().getId().equals(examPeriodId))
                .map(ExamResultResponse::from).toList();
    }

    /** END_SEMESTER and SUPPLEMENTARY (the re-sit of the end-semester exam) are entered by the COE only. */
    public static boolean isEndSemesterType(ExamPeriod.ExamType type) {
        return type == ExamPeriod.ExamType.END_SEMESTER || type == ExamPeriod.ExamType.SUPPLEMENTARY;
    }

    static boolean isCoe() {
        return SecurityUtils.hasAuthority("ROLE_COE");
    }

    /**
     * End-semester / supplementary results: COE only (any offering, active or not, since results arrive after
     * the term). STAFF are always refused, also for their own offerings. INTERNAL results: the assigned staff
     * member of an active offering, never the COE.
     */
    private static void requireResultEntry(SubjectOffering offering, ExamPeriod period) {
        if (isEndSemesterType(period.getExamType())) {
            if (!isCoe()) {
                throw new ForbiddenException("End-semester and supplementary results are read-only for staff; "
                        + "they are entered by the Controller of Examinations");
            }
            return;
        }
        if (isCoe()) {
            throw new ForbiddenException("Internal exam results are entered by the staff member assigned to the subject offering");
        }
        OfferingAccess.requireManage(offering);
    }

    private static Optional<String> validate(BulkResultRequest.Entry e) {
        BigDecimal marks = e.marksObtained();
        switch (e.resultStatus()) {
            case PASS, FAIL -> {
                if (marks == null) return Optional.of("marksObtained is required when status is " + e.resultStatus());
            }
            case ABSENT -> {
                if (marks != null) return Optional.of("marksObtained must be empty when status is ABSENT");
                if (e.grade() != null && !e.grade().isBlank()) return Optional.of("grade must be empty when status is ABSENT");
            }
            case WITHHELD -> { }
        }
        if (marks != null) {
            if (marks.signum() < 0) return Optional.of("marksObtained cannot be negative");
            if (marks.compareTo(e.maxMarks()) > 0) return Optional.of("marksObtained cannot exceed maxMarks");
        }
        return Optional.empty();
    }

    private static boolean same(ExamResult r, BulkResultRequest.Entry e, String grade, String remarks) {
        return r.getResultStatus() == e.resultStatus() && equal(r.getMarksObtained(), e.marksObtained())
                && equal(r.getMaxMarks(), e.maxMarks()) && Objects.equals(r.getGrade(), grade)
                && Objects.equals(r.getRemarks(), remarks);
    }

    private static boolean equal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static ResultSheetResponse.Row row(Student s, boolean enrolled, ExamResult r) {
        return new ResultSheetResponse.Row(s.getId(), s.getRegisterNumber(), s.getName(), enrolled,
                r == null ? null : r.getId(), r == null ? null : r.getMarksObtained(), r == null ? null : r.getMaxMarks(),
                r == null ? null : r.getGrade(), r == null ? NOT_ENTERED : r.getResultStatus().name(),
                r == null ? null : r.getRemarks(), r != null && r.isPublished());
    }

    private Map<Long, ExamResult> byStudent(Long offeringId, Long periodId) {
        return results.findBySubjectOfferingIdAndExamPeriodId(offeringId, periodId).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity()));
    }

    private SubjectOffering requireOffering(Long id) {
        return offerings.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", id));
    }

    private ExamPeriod requirePeriod(Long periodId, SubjectOffering offering) {
        ExamPeriod period = periods.findById(periodId).orElseThrow(() -> new ResourceNotFoundException("ExamPeriod", "id", periodId));
        if (!period.getAcademicYear().equals(offering.getAcademicYear()) || !period.getSemester().equals(offering.getSemester())) {
            throw new BadRequestException("Exam period " + period.getName() + " is for " + period.getAcademicYear() + " semester "
                    + period.getSemester() + ", but this subject offering is " + offering.getAcademicYear() + " semester "
                    + offering.getSemester());
        }
        return period;
    }
}
