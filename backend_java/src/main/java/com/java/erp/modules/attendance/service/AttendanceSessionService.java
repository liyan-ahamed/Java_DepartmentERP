package com.java.erp.modules.attendance.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.attendance.dto.request.BulkAttendanceRequest;
import com.java.erp.modules.attendance.dto.request.CreateAttendanceSessionRequest;
import com.java.erp.modules.attendance.dto.request.UpdateAttendanceSessionRequest;
import com.java.erp.modules.attendance.dto.response.AttendanceSessionResponse;
import com.java.erp.modules.attendance.dto.response.BulkAttendanceResponse;
import com.java.erp.modules.attendance.dto.response.SessionRecordsResponse;
import com.java.erp.modules.attendance.entity.AttendanceSession;
import com.java.erp.modules.attendance.entity.StudentAttendance;
import com.java.erp.modules.attendance.repository.AttendanceSessionRepository;
import com.java.erp.modules.attendance.repository.StudentAttendanceRepository;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.timetable.entity.TimetableSlot;
import com.java.erp.modules.timetable.repository.TimetableSlotRepository;
import jakarta.persistence.criteria.Join;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attendance sessions and marking. Only the STAFF member assigned to an (active) offering may create,
 * mark, edit, delete or finalize its sessions; HOD reads everything; STUDENT has no access here.
 * The expected class list is the offering's ENROLLED active students. Missing students are never
 * marked ABSENT automatically: finalization fails until everyone is marked.
 */
@Service
public class AttendanceSessionService {

    static final String MODULE = "ATTENDANCE";
    static final String ENTITY_TYPE = "AttendanceSession";
    static final String NOT_MARKED = "NOT_MARKED";

    private final AttendanceSessionRepository sessions;
    private final StudentAttendanceRepository records;
    private final SubjectOfferingRepository offerings;
    private final TimetableSlotRepository slots;
    private final UserRepository users;
    private final EnrollmentRoster roster;
    private final AttendanceSummaryService summaries;
    private final AuditRecorder audit;

    public AttendanceSessionService(AttendanceSessionRepository sessions, StudentAttendanceRepository records,
                                    SubjectOfferingRepository offerings, TimetableSlotRepository slots,
                                    UserRepository users, EnrollmentRoster roster, AttendanceSummaryService summaries,
                                    AuditRecorder audit) {
        this.sessions = sessions;
        this.records = records;
        this.offerings = offerings;
        this.slots = slots;
        this.users = users;
        this.roster = roster;
        this.summaries = summaries;
        this.audit = audit;
    }

    @Transactional
    public AttendanceSessionResponse create(CreateAttendanceSessionRequest request) {
        SubjectOffering offering = offerings.findWithDetailsById(request.subjectOfferingId())
                .orElseThrow(() -> new BadRequestException("Subject offering " + request.subjectOfferingId() + " does not exist"));
        OfferingAccess.requireManage(offering);

        AttendanceSession session = new AttendanceSession();
        session.setSubjectOffering(offering);
        session.setCreatedBy(users.getReferenceById(SecurityUtils.getCurrentUserId()));
        apply(session, request.attendanceDate(), request.timetableSlotId(), request.periodNumber(), request.topic(), 0L);
        session = sessions.saveAndFlush(session);
        audit.record("CREATE", MODULE, ENTITY_TYPE, session.getId(), Map.of(
                "sessionId", session.getId(), "subjectOfferingId", offering.getId(),
                "attendanceDate", session.getAttendanceDate().toString()));
        return AttendanceSessionResponse.from(requireSession(session.getId()));
    }

    @Transactional
    public AttendanceSessionResponse update(Long id, UpdateAttendanceSessionRequest request) {
        AttendanceSession session = requireManageableDraft(id);
        apply(session, request.attendanceDate(), request.timetableSlotId(), request.periodNumber(), request.topic(), id);
        sessions.saveAndFlush(session);
        audit.record("UPDATE", MODULE, ENTITY_TYPE, id, Map.of(
                "sessionId", id, "attendanceDate", session.getAttendanceDate().toString()));
        return AttendanceSessionResponse.from(session);
    }

    /** Deletes a DRAFT session and its marks (student_attendance rows cascade in the database). */
    @Transactional
    public void delete(Long id) {
        AttendanceSession session = requireManageableDraft(id);
        Long offeringId = session.getSubjectOffering().getId();
        sessions.delete(session);
        sessions.flush();
        audit.record("DELETE", MODULE, ENTITY_TYPE, id, Map.of("sessionId", id, "subjectOfferingId", offeringId));
    }

    @Transactional(readOnly = true)
    public AttendanceSessionResponse get(Long id) {
        AttendanceSession session = requireSession(id);
        OfferingAccess.requireView(session.getSubjectOffering());
        return AttendanceSessionResponse.from(session);
    }

    /** HOD sees all sessions; STAFF only those of their offerings. */
    @Transactional(readOnly = true)
    public PagedResponse<AttendanceSessionResponse> list(Long subjectOfferingId, Long sectionId, AttendanceSession.Status status,
                                                         LocalDate from, LocalDate to, int page, int size) {
        Long staffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        Specification<AttendanceSession> spec = (root, query, cb) -> {
            Join<AttendanceSession, SubjectOffering> o = root.join("subjectOffering");
            List<Predicate> p = new ArrayList<>();
            if (staffId != null) p.add(cb.equal(o.get("staff").get("id"), staffId));
            if (subjectOfferingId != null) p.add(cb.equal(o.get("id"), subjectOfferingId));
            if (sectionId != null) p.add(cb.equal(o.get("section").get("id"), sectionId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("attendanceDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("attendanceDate"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<AttendanceSession> result = sessions.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("attendanceDate"), Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream().map(AttendanceSessionResponse::from).toList(), result);
    }

    /** The full roster: every ENROLLED student with their mark or NOT_MARKED. */
    @Transactional(readOnly = true)
    public SessionRecordsResponse records(Long id) {
        AttendanceSession session = requireSession(id);
        OfferingAccess.requireView(session.getSubjectOffering());
        Map<Long, Student> classList = roster.classList(session.getSubjectOffering().getId());
        Map<Long, StudentAttendance> marks = marksByStudent(id);

        List<SessionRecordsResponse.Row> rows = new ArrayList<>();
        int marked = 0;
        for (Student s : classList.values()) {
            StudentAttendance r = marks.get(s.getId());
            if (r != null) marked++;
            rows.add(row(s, true, r));
        }
        for (StudentAttendance r : marks.values()) {
            if (!classList.containsKey(r.getStudent().getId())) rows.add(row(r.getStudent(), false, r));
        }
        return new SessionRecordsResponse(AttendanceSessionResponse.from(session), classList.size(), marked,
                classList.size() - marked, rows);
    }

    /**
     * Creates or updates marks of a DRAFT session. Every row is validated first (student enrolled,
     * listed once); any problem rejects the whole request and nothing is saved.
     */
    @Transactional
    public BulkAttendanceResponse saveRecords(Long id, BulkAttendanceRequest request) {
        AttendanceSession session = requireManageableDraft(id);
        Map<Long, Student> classList = roster.classList(session.getSubjectOffering().getId());

        List<String> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.records().size(); i++) {
            BulkAttendanceRequest.Entry e = request.records().get(i);
            String row = "records[" + i + "] (studentId " + e.studentId() + "): ";
            if (!seen.add(e.studentId())) errors.add(row + "student appears more than once");
            else if (!classList.containsKey(e.studentId())) errors.add(row + "student is not enrolled in this subject offering");
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Attendance validation failed", errors);
        }

        Map<Long, StudentAttendance> existing = marksByStudent(id);
        int created = 0, updated = 0, unchanged = 0;
        List<StudentAttendance> toSave = new ArrayList<>();
        for (BulkAttendanceRequest.Entry e : request.records()) {
            String remarks = e.remarks() == null || e.remarks().isBlank() ? null : e.remarks().trim();
            StudentAttendance r = existing.get(e.studentId());
            if (r == null) {
                r = new StudentAttendance(session, classList.get(e.studentId()));
                created++;
            } else if (r.getStatus() == e.status() && Objects.equals(r.getRemarks(), remarks)) {
                unchanged++;
                continue;
            } else {
                updated++;
            }
            r.setStatus(e.status());
            r.setRemarks(remarks);
            toSave.add(r);
        }
        records.saveAll(toSave);
        records.flush();

        int notMarked = (int) (classList.size() - records.countMarked(id, classList.keySet().isEmpty() ? Set.of(-1L) : classList.keySet()));
        audit.record("BULK_SAVE", MODULE, ENTITY_TYPE, id, Map.of(
                "sessionId", id, "received", request.records().size(), "created", created, "updated", updated));
        return new BulkAttendanceResponse(id, request.records().size(), created, updated, unchanged, classList.size(), notMarked);
    }

    /**
     * DRAFT -> FINALIZED (single guarded update: a retry gets 409). Requires every ENROLLED student
     * to be marked. Afterwards students below the minimum get a shortage notice (with cooldown).
     */
    @Transactional
    public AttendanceSessionResponse finalizeSession(Long id) {
        AttendanceSession session = requireManageableDraft(id);
        Map<Long, Student> classList = roster.classList(session.getSubjectOffering().getId());
        if (classList.isEmpty()) {
            throw new BadRequestException("No students are enrolled in this subject offering");
        }
        Set<Long> marked = marksByStudent(id).keySet();
        List<String> missing = classList.values().stream().filter(s -> !marked.contains(s.getId()))
                .map(s -> s.getRegisterNumber() + " " + s.getName()).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException(missing.size() + " enrolled student(s) are not marked yet",
                    missing.size() > 50 ? missing.subList(0, 50) : missing);
        }
        if (sessions.finalizeIfDraft(id, LocalDateTime.now(), AttendanceSession.Status.DRAFT,
                AttendanceSession.Status.FINALIZED) == 0) {
            throw new ConflictException("This attendance session is already finalized");
        }
        AttendanceSession finalized = requireSession(id);
        int notified = summaries.notifyShortages(finalized.getSubjectOffering());
        audit.record("FINALIZE", MODULE, ENTITY_TYPE, id, Map.of(
                "sessionId", id, "subjectOfferingId", finalized.getSubjectOffering().getId(),
                "studentsMarked", classList.size(), "shortageNotifications", notified));
        return AttendanceSessionResponse.from(finalized);
    }

    private void apply(AttendanceSession session, LocalDate date, Long slotId, Integer periodNumber, String topic, Long excludeId) {
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Attendance date cannot be in the future");
        }
        TimetableSlot slot = null;
        if (slotId != null) {
            slot = slots.findById(slotId)
                    .orElseThrow(() -> new BadRequestException("Timetable slot " + slotId + " does not exist"));
            if (!slot.getSubjectOffering().getId().equals(session.getSubjectOffering().getId())) {
                throw new BadRequestException("Timetable slot " + slotId + " belongs to another subject offering");
            }
            if (slot.getDayOfWeek() != date.getDayOfWeek()) {
                throw new BadRequestException("Timetable slot " + slotId + " is on " + slot.getDayOfWeek()
                        + " but the attendance date is a " + date.getDayOfWeek());
            }
        }
        Integer period = periodNumber != null ? periodNumber : slot != null ? slot.getPeriodNumber() : null;
        if (period != null && sessions.existsBySubjectOfferingIdAndAttendanceDateAndPeriodNumberAndIdNot(
                session.getSubjectOffering().getId(), date, period, excludeId)) {
            throw new ConflictException("An attendance session already exists for this subject offering on "
                    + date + " period " + period);
        }
        session.setAttendanceDate(date);
        session.setTimetableSlot(slot);
        session.setPeriodNumber(period);
        session.setTopic(topic == null || topic.isBlank() ? null : topic.trim());
    }

    private AttendanceSession requireManageableDraft(Long id) {
        AttendanceSession session = requireSession(id);
        OfferingAccess.requireManage(session.getSubjectOffering());
        if (!session.isDraft()) {
            throw new ConflictException("This attendance session is finalized and can no longer be changed");
        }
        return session;
    }

    private AttendanceSession requireSession(Long id) {
        return sessions.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("AttendanceSession", "id", id));
    }

    private Map<Long, StudentAttendance> marksByStudent(Long sessionId) {
        return records.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity()));
    }

    private static SessionRecordsResponse.Row row(Student s, boolean inRoster, StudentAttendance r) {
        return new SessionRecordsResponse.Row(s.getId(), s.getRegisterNumber(), s.getName(), inRoster,
                r == null ? null : r.getId(), r == null ? NOT_MARKED : r.getStatus().name(),
                r == null ? null : r.getRemarks(), r == null ? null : r.getUpdatedAt());
    }
}
