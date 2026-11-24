package com.java.erp.modules.exams.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.exams.dto.request.ExamPeriodRequest;
import com.java.erp.modules.exams.dto.response.ExamPeriodResponse;
import com.java.erp.modules.exams.entity.ExamPeriod;
import com.java.erp.modules.exams.repository.ExamPeriodRepository;
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

import java.util.*;

/** Exam periods: HOD manages (DRAFT -> OPEN -> CLOSED); HOD and STAFF can read them. */
@Service
public class ExamPeriodService {

    static final String MODULE = "EXAMS";
    static final String ENTITY_TYPE = "ExamPeriod";

    private final ExamPeriodRepository periods;
    private final SubjectOfferingRepository offerings;
    private final UserRepository users;
    private final EnrollmentRoster roster;
    private final StudentNotifier notifier;
    private final AuditRecorder audit;

    public ExamPeriodService(ExamPeriodRepository periods, SubjectOfferingRepository offerings, UserRepository users,
                             EnrollmentRoster roster, StudentNotifier notifier, AuditRecorder audit) {
        this.periods = periods;
        this.offerings = offerings;
        this.users = users;
        this.roster = roster;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional
    public ExamPeriodResponse create(ExamPeriodRequest request) {
        validate(request);
        ExamPeriod period = new ExamPeriod();
        period.setCreatedBy(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        period.setAcademicYear(request.academicYear());
        period.setSemester(request.semester());
        period.setExamType(request.examType());
        applyEditable(period, request);
        period = periods.saveAndFlush(period);
        log("CREATE", period, null);
        return ExamPeriodResponse.from(period);
    }

    /** DRAFT: everything. OPEN: name and registration dates only. CLOSED: nothing. */
    @Transactional
    public ExamPeriodResponse update(Long id, ExamPeriodRequest request) {
        ExamPeriod period = require(id);
        validate(request);
        switch (period.getStatus()) {
            case CLOSED -> throw new ConflictException("A CLOSED exam period cannot be edited");
            case OPEN -> {
                if (!period.getAcademicYear().equals(request.academicYear()) || !period.getSemester().equals(request.semester())
                        || period.getExamType() != request.examType()) {
                    throw new ConflictException("Term and exam type of an OPEN exam period cannot change");
                }
            }
            case DRAFT -> {
                period.setAcademicYear(request.academicYear());
                period.setSemester(request.semester());
                period.setExamType(request.examType());
            }
        }
        applyEditable(period, request);
        periods.saveAndFlush(period);
        log("UPDATE", period, period.getStatus());
        return ExamPeriodResponse.from(period);
    }

    /** DRAFT -> OPEN; students enrolled in the term's subjects are notified once. */
    @Transactional
    public ExamPeriodResponse open(Long id) {
        ExamPeriod period = require(id);
        if (period.getStatus() != ExamPeriod.Status.DRAFT) {
            throw new ConflictException("Only a DRAFT exam period can be opened (it is " + period.getStatus() + ")");
        }
        period.setStatus(ExamPeriod.Status.OPEN);
        periods.saveAndFlush(period);

        Map<Long, Student> eligible = new LinkedHashMap<>();
        for (Long offeringId : offerings.findActiveIdsByTerm(period.getAcademicYear(), period.getSemester())) {
            eligible.putAll(roster.classList(offeringId));
        }
        int notified = notifier.notifyAll(eligible.values(), new StudentNotifier.Message(
                Notification.NotificationType.ACTION_REQUIRED, "Exam registration open: " + period.getName(),
                "Registration for " + period.getName() + " is open from " + period.getRegistrationStart() + " to "
                        + period.getRegistrationEnd() + ".", MODULE, period.getId(), "EXAM_PERIOD",
                Notification.NotificationPriority.HIGH));
        Map<String, Object> m = summary(period, ExamPeriod.Status.DRAFT);
        m.put("notified", notified);
        audit.record("OPEN", MODULE, ENTITY_TYPE, id, m);
        return ExamPeriodResponse.from(period);
    }

    /** OPEN -> CLOSED (final). */
    @Transactional
    public ExamPeriodResponse close(Long id) {
        ExamPeriod period = require(id);
        if (period.getStatus() != ExamPeriod.Status.OPEN) {
            throw new ConflictException("Only an OPEN exam period can be closed (it is " + period.getStatus() + ")");
        }
        period.setStatus(ExamPeriod.Status.CLOSED);
        periods.saveAndFlush(period);
        log("CLOSE", period, ExamPeriod.Status.OPEN);
        return ExamPeriodResponse.from(period);
    }

    @Transactional(readOnly = true)
    public ExamPeriodResponse get(Long id) {
        return ExamPeriodResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExamPeriodResponse> list(String academicYear, Integer semester, ExamPeriod.ExamType examType,
                                                  ExamPeriod.Status status, int page, int size) {
        Specification<ExamPeriod> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (academicYear != null) p.add(cb.equal(root.get("academicYear"), academicYear.trim()));
            if (semester != null) p.add(cb.equal(root.get("semester"), semester));
            if (examType != null) p.add(cb.equal(root.get("examType"), examType));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<ExamPeriod> result = periods.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("registrationStart"), Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream().map(ExamPeriodResponse::from).toList(), result);
    }

    ExamPeriod require(Long id) {
        return periods.findById(id).orElseThrow(() -> new ResourceNotFoundException("ExamPeriod", "id", id));
    }

    private static void validate(ExamPeriodRequest r) {
        if (r.registrationEnd().isBefore(r.registrationStart())) {
            throw new BadRequestException("Registration end cannot be before registration start");
        }
        int start = Integer.parseInt(r.academicYear().substring(0, 4));
        if (Integer.parseInt(r.academicYear().substring(5)) != start + 1) {
            throw new BadRequestException("Academic year must span consecutive years, e.g. 2026-2027");
        }
    }

    private static void applyEditable(ExamPeriod period, ExamPeriodRequest r) {
        period.setName(r.name().trim());
        period.setRegistrationStart(r.registrationStart());
        period.setRegistrationEnd(r.registrationEnd());
    }

    private void log(String action, ExamPeriod period, ExamPeriod.Status oldStatus) {
        audit.record(action, MODULE, ENTITY_TYPE, period.getId(), summary(period, oldStatus));
    }

    private static Map<String, Object> summary(ExamPeriod period, ExamPeriod.Status oldStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examPeriodId", period.getId());
        m.put("examType", period.getExamType());
        m.put("academicYear", period.getAcademicYear());
        m.put("semester", period.getSemester());
        m.put("oldStatus", oldStatus);
        m.put("newStatus", period.getStatus());
        return m;
    }
}
