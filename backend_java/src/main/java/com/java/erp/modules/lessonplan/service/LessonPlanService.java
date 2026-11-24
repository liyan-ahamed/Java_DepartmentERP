package com.java.erp.modules.lessonplan.service;

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
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.lessonplan.dto.request.CompleteLessonPlanRequest;
import com.java.erp.modules.lessonplan.dto.request.CreateLessonPlanRequest;
import com.java.erp.modules.lessonplan.dto.request.UpdateLessonPlanRequest;
import com.java.erp.modules.lessonplan.dto.response.LessonPlanProgressResponse;
import com.java.erp.modules.lessonplan.dto.response.LessonPlanResponse;
import com.java.erp.modules.lessonplan.entity.LessonPlan;
import com.java.erp.modules.lessonplan.repository.LessonPlanRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Lesson plans (what is taught when). The assigned STAFF member manages the plans of their active
 * offerings; HOD reads all; STUDENT reads plans of offerings they are (or were) enrolled in.
 */
@Service
public class LessonPlanService {

    static final String MODULE = "LESSON_PLAN";
    static final String ENTITY_TYPE = "LessonPlan";

    private final LessonPlanRepository plans;
    private final SubjectOfferingRepository offerings;
    private final UserRepository users;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;
    private final AuditRecorder audit;

    public LessonPlanService(LessonPlanRepository plans, SubjectOfferingRepository offerings, UserRepository users,
                             EnrollmentRoster roster, CurrentStudentResolver currentStudent, AuditRecorder audit) {
        this.plans = plans;
        this.offerings = offerings;
        this.users = users;
        this.roster = roster;
        this.currentStudent = currentStudent;
        this.audit = audit;
    }

    @Transactional
    public LessonPlanResponse create(CreateLessonPlanRequest request) {
        SubjectOffering offering = offerings.findWithDetailsById(request.subjectOfferingId())
                .orElseThrow(() -> new BadRequestException("Subject offering " + request.subjectOfferingId() + " does not exist"));
        OfferingAccess.requireManage(offering);

        LessonPlan plan = new LessonPlan();
        plan.setSubjectOffering(offering);
        plan.setCreatedBy(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        plan.setUnitNumber(request.unitNumber());
        plan.setTopic(request.topic().trim());
        plan.setDescription(trimToNull(request.description()));
        plan.setPlannedDate(request.plannedDate());
        plan.setNotes(trimToNull(request.notes()));
        plan = plans.saveAndFlush(plan);
        log("CREATE", plan, null);
        return LessonPlanResponse.from(plan);
    }

    @Transactional
    public LessonPlanResponse update(Long id, UpdateLessonPlanRequest request) {
        LessonPlan plan = requireManageable(id);
        if (plan.getStatus() != LessonPlan.Status.PLANNED) {
            throw new ConflictException("Only PLANNED topics can be edited; reopen it first");
        }
        plan.setUnitNumber(request.unitNumber());
        plan.setTopic(request.topic().trim());
        plan.setDescription(trimToNull(request.description()));
        plan.setPlannedDate(request.plannedDate());
        plan.setNotes(trimToNull(request.notes()));
        plans.saveAndFlush(plan);
        log("UPDATE", plan, LessonPlan.Status.PLANNED);
        return LessonPlanResponse.from(plan);
    }

    @Transactional
    public LessonPlanResponse complete(Long id, CompleteLessonPlanRequest request) {
        LessonPlan plan = requireManageable(id);
        requireStatus(plan, LessonPlan.Status.PLANNED, "completed");
        LocalDate date = request == null || request.completedDate() == null ? LocalDate.now() : request.completedDate();
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Completed date cannot be in the future");
        }
        plan.setStatus(LessonPlan.Status.COMPLETED);
        plan.setCompletedDate(date);
        if (request != null && request.notes() != null) plan.setNotes(trimToNull(request.notes()));
        plans.saveAndFlush(plan);
        log("COMPLETE", plan, LessonPlan.Status.PLANNED);
        return LessonPlanResponse.from(plan);
    }

    @Transactional
    public LessonPlanResponse cancel(Long id) {
        LessonPlan plan = requireManageable(id);
        requireStatus(plan, LessonPlan.Status.PLANNED, "cancelled");
        plan.setStatus(LessonPlan.Status.CANCELLED);
        plans.saveAndFlush(plan);
        log("CANCEL", plan, LessonPlan.Status.PLANNED);
        return LessonPlanResponse.from(plan);
    }

    /** COMPLETED or CANCELLED -> PLANNED (corrects a mistake). */
    @Transactional
    public LessonPlanResponse reopen(Long id) {
        LessonPlan plan = requireManageable(id);
        LessonPlan.Status old = plan.getStatus();
        if (old == LessonPlan.Status.PLANNED) {
            throw new ConflictException("This topic is already PLANNED");
        }
        plan.setStatus(LessonPlan.Status.PLANNED);
        plan.setCompletedDate(null);
        plans.saveAndFlush(plan);
        log("REOPEN", plan, old);
        return LessonPlanResponse.from(plan);
    }

    @Transactional(readOnly = true)
    public LessonPlanResponse get(Long id) {
        LessonPlan plan = requirePlan(id);
        requireRead(plan.getSubjectOffering());
        return LessonPlanResponse.from(plan);
    }

    /** HOD: all plans; STAFF: plans of their own offerings. */
    @Transactional(readOnly = true)
    public PagedResponse<LessonPlanResponse> list(Long subjectOfferingId, Long sectionId, Long subjectId, Long staffId,
                                                  LessonPlan.Status status, int page, int size) {
        Long ownStaffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        Specification<LessonPlan> spec = (root, query, cb) -> {
            Join<LessonPlan, SubjectOffering> o = root.join("subjectOffering");
            List<Predicate> p = new ArrayList<>();
            if (ownStaffId != null) p.add(cb.equal(o.get("staff").get("id"), ownStaffId));
            if (subjectOfferingId != null) p.add(cb.equal(o.get("id"), subjectOfferingId));
            if (sectionId != null) p.add(cb.equal(o.get("section").get("id"), sectionId));
            if (subjectId != null) p.add(cb.equal(o.get("subject").get("id"), subjectId));
            if (staffId != null) p.add(cb.equal(o.get("staff").get("id"), staffId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<LessonPlan> result = plans.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("plannedDate"), Sort.Order.asc("id"))));
        return PagedResponse.of(result.getContent().stream().map(LessonPlanResponse::from).toList(), result);
    }

    /** STUDENT: plans of offerings they are enrolled in (or completed), optionally one offering. */
    @Transactional(readOnly = true)
    public List<LessonPlanResponse> myPlans(Long subjectOfferingId) {
        Student student = currentStudent.require();
        List<Long> ids = roster.offeringIds(student.getId(), EnrollmentRoster.CURRENT_OR_COMPLETED);
        if (subjectOfferingId != null) {
            if (!ids.contains(subjectOfferingId)) {
                throw new ForbiddenException("You are not enrolled in this subject offering");
            }
            ids = List.of(subjectOfferingId);
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return plans.findBySubjectOfferingIdInOrderBySubjectOfferingIdAscPlannedDateAscIdAsc(ids).stream()
                .map(LessonPlanResponse::from).toList();
    }

    /** HOD, the assigned STAFF member, or an enrolled STUDENT. */
    @Transactional(readOnly = true)
    public LessonPlanProgressResponse progress(Long offeringId) {
        SubjectOffering offering = offerings.findWithDetailsById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", "id", offeringId));
        requireRead(offering);
        Map<LessonPlan.Status, Long> counts = new EnumMap<>(LessonPlan.Status.class);
        plans.countByStatus(offeringId).forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        long completed = counts.getOrDefault(LessonPlan.Status.COMPLETED, 0L);
        long remaining = counts.getOrDefault(LessonPlan.Status.PLANNED, 0L);
        long total = completed + remaining;
        BigDecimal percentage = total == 0 ? null
                : BigDecimal.valueOf(completed * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new LessonPlanProgressResponse(offeringId, offering.getSubject().getCode(), offering.getSubject().getName(),
                total, completed, remaining, counts.getOrDefault(LessonPlan.Status.CANCELLED, 0L), percentage);
    }

    private void requireRead(SubjectOffering offering) {
        if (SecurityUtils.hasAuthority("ROLE_STUDENT")) {
            Student student = currentStudent.require();
            if (!roster.isEnrolled(student.getId(), offering.getId(), EnrollmentRoster.CURRENT_OR_COMPLETED)) {
                throw new ForbiddenException("You are not enrolled in this subject offering");
            }
            return;
        }
        OfferingAccess.requireView(offering);
    }

    private LessonPlan requireManageable(Long id) {
        LessonPlan plan = requirePlan(id);
        OfferingAccess.requireManage(plan.getSubjectOffering());
        return plan;
    }

    private LessonPlan requirePlan(Long id) {
        return plans.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("LessonPlan", "id", id));
    }

    private static void requireStatus(LessonPlan plan, LessonPlan.Status expected, String action) {
        if (plan.getStatus() != expected) {
            throw new ConflictException("A " + plan.getStatus() + " topic cannot be " + action);
        }
    }

    private void log(String action, LessonPlan plan, LessonPlan.Status oldStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lessonPlanId", plan.getId());
        m.put("subjectOfferingId", plan.getSubjectOffering().getId());
        m.put("oldStatus", oldStatus);
        m.put("newStatus", plan.getStatus());
        audit.record(action, MODULE, ENTITY_TYPE, plan.getId(), m);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
