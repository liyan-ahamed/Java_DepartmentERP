package com.java.erp.modules.timetable.service;

import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.academic.entity.SubjectOffering;
import com.java.erp.modules.academic.repository.SubjectOfferingRepository;
import com.java.erp.modules.academic.service.OfferingAccess;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.enrollment.service.EnrollmentRoster;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.holiday.dto.response.HolidayResponse;
import com.java.erp.modules.holiday.service.HolidayService;
import com.java.erp.modules.timetable.dto.request.TimetableSlotRequest;
import com.java.erp.modules.timetable.dto.response.PeriodResponse;
import com.java.erp.modules.timetable.dto.response.TimetableSlotResponse;
import com.java.erp.modules.timetable.entity.TimetableSlot;
import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule;
import com.java.erp.modules.timetable.period.InstitutionPeriodSchedule.Period;
import com.java.erp.modules.timetable.repository.TimetableSlotRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recurring weekly timetable. HOD manages slots manually (no generation); STAFF read the slots of
 * their offerings; STUDENT reads the slots of offerings they are ENROLLED in.
 *
 * Every slot is one official teaching period ({@link InstitutionPeriodSchedule}); break and lunch
 * can never hold a class. Conflict rule: two active slots of active offerings may not overlap on the
 * same day when they share the section or the staff member.
 */
@Service
public class TimetableService {

    static final String MODULE = "TIMETABLE";
    static final String ENTITY_TYPE = "TimetableSlot";
    /** Advisory-lock key for timetable writes ("TT" in ASCII). */
    private static final long LOCK_KEY = 0x5454L;

    private static final Comparator<TimetableSlot> WEEK_ORDER = Comparator
            .comparing(TimetableSlot::getDayOfWeek)
            .thenComparing(TimetableSlot::getStartTime)
            .thenComparing(t -> t.getSubjectOffering().getSection().getId())
            .thenComparing(TimetableSlot::getId);

    private final TimetableSlotRepository slots;
    private final SubjectOfferingRepository offerings;
    private final EnrollmentRoster roster;
    private final CurrentStudentResolver currentStudent;
    private final AuditRecorder audit;
    private final HolidayService holidays;

    public TimetableService(TimetableSlotRepository slots, SubjectOfferingRepository offerings, EnrollmentRoster roster,
                            CurrentStudentResolver currentStudent, AuditRecorder audit, HolidayService holidays) {
        this.slots = slots;
        this.offerings = offerings;
        this.roster = roster;
        this.currentStudent = currentStudent;
        this.audit = audit;
        this.holidays = holidays;
    }

    /** The official daily schedule (8 teaching periods, break, lunch) in time order. */
    public List<PeriodResponse> periods() {
        return InstitutionPeriodSchedule.DAY.stream().map(PeriodResponse::from).toList();
    }

    @Transactional
    public TimetableSlotResponse create(TimetableSlotRequest request) {
        SubjectOffering offering = requireActiveOffering(request.subjectOfferingId());
        Period period = resolvePeriod(request);
        slots.lockTimetable(LOCK_KEY);
        rejectConflicts(request.dayOfWeek(), period, offering, 0L);

        TimetableSlot slot = new TimetableSlot();
        apply(slot, request, period, offering);
        slot = slots.saveAndFlush(slot);
        audit.record("CREATE", MODULE, ENTITY_TYPE, slot.getId(), summary(slot));
        return TimetableSlotResponse.from(slot);
    }

    @Transactional
    public TimetableSlotResponse update(Long id, TimetableSlotRequest request) {
        TimetableSlot slot = requireSlot(id);
        if (!slot.isActive()) {
            throw new ConflictException("This timetable slot has been deleted");
        }
        SubjectOffering offering = requireActiveOffering(request.subjectOfferingId());
        Period period = resolvePeriod(request);
        slots.lockTimetable(LOCK_KEY);
        rejectConflicts(request.dayOfWeek(), period, offering, id);

        apply(slot, request, period, offering);
        slots.saveAndFlush(slot);
        audit.record("UPDATE", MODULE, ENTITY_TYPE, id, summary(slot));
        return TimetableSlotResponse.from(slot);
    }

    /** Soft delete: the slot is deactivated (attendance sessions may still reference it). */
    @Transactional
    public void delete(Long id) {
        TimetableSlot slot = requireSlot(id);
        if (!slot.isActive()) {
            return;
        }
        slot.setActive(false);
        slots.saveAndFlush(slot);
        audit.record("DELETE", MODULE, ENTITY_TYPE, id, summary(slot));
    }

    @Transactional(readOnly = true)
    public TimetableSlotResponse get(Long id) {
        TimetableSlot slot = requireSlot(id);
        OfferingAccess.requireView(slot.getSubjectOffering());
        return TimetableSlotResponse.from(slot);
    }

    /**
     * HOD: all slots; STAFF: slots of their own offerings. Active slots only unless active=false is asked for.
     * weekOf (any date of the wanted week): each slot also gets its date in that week and the holidays of that date.
     */
    @Transactional(readOnly = true)
    public List<TimetableSlotResponse> list(Long batchId, Integer semester, Long sectionId, Long staffId,
                                            Long subjectOfferingId, DayOfWeek dayOfWeek, Boolean active, LocalDate weekOf) {
        Long ownStaffId = OfferingAccess.isHod() ? null : SecurityUtils.getCurrentUserId();
        boolean activeOnly = active == null || active;
        Specification<TimetableSlot> spec = (root, query, cb) -> {
            Join<TimetableSlot, SubjectOffering> o = root.join("subjectOffering");
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("active"), activeOnly));
            if (ownStaffId != null) p.add(cb.equal(o.get("staff").get("id"), ownStaffId));
            if (batchId != null) p.add(cb.equal(o.get("batch").get("id"), batchId));
            if (semester != null) p.add(cb.equal(o.get("semester"), semester));
            if (sectionId != null) p.add(cb.equal(o.get("section").get("id"), sectionId));
            if (staffId != null) p.add(cb.equal(o.get("staff").get("id"), staffId));
            if (subjectOfferingId != null) p.add(cb.equal(o.get("id"), subjectOfferingId));
            if (dayOfWeek != null) p.add(cb.equal(root.get("dayOfWeek"), dayOfWeek));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return toResponses(slots.findAll(spec), weekOf);
    }

    /** STAFF: own active offerings. STUDENT: active offerings they are ENROLLED in. */
    @Transactional(readOnly = true)
    public List<TimetableSlotResponse> myTimetable(LocalDate weekOf) {
        Collection<Long> offeringIds;
        if (SecurityUtils.hasAuthority("ROLE_STUDENT")) {
            offeringIds = roster.offeringIds(currentStudent.require().getId(), EnrollmentRoster.CURRENT);
        } else {
            Long staffId = SecurityUtils.getCurrentUserId();
            offeringIds = offerings.findActiveIdsByStaffId(staffId);
        }
        if (offeringIds.isEmpty()) {
            return List.of();
        }
        return toResponses(slots.findActiveByOfferingIds(offeringIds), weekOf);
    }

    private List<TimetableSlotResponse> toResponses(List<TimetableSlot> found, LocalDate weekOf) {
        List<TimetableSlot> sorted = found.stream().sorted(WEEK_ORDER).toList();
        if (weekOf == null) {
            return sorted.stream().map(TimetableSlotResponse::from).toList();
        }
        LocalDate monday = weekOf.with(DayOfWeek.MONDAY);
        Map<LocalDate, List<HolidayResponse>> byDate = holidays.byDate(monday, monday.plusDays(6));
        return sorted.stream().map(t -> {
            LocalDate date = monday.plusDays(t.getDayOfWeek().getValue() - 1L);
            return TimetableSlotResponse.onDate(t, date,
                    HolidayService.applicable(byDate.get(date), t.getSubjectOffering().getBatch().getId()));
        }).toList();
    }

    /**
     * The official teaching period of the request. periodNumber decides; startTime/endTime, when sent, must
     * equal its times. Without periodNumber the times must match one teaching period exactly.
     */
    static Period resolvePeriod(TimetableSlotRequest request) {
        LocalTime start = request.startTime();
        LocalTime end = request.endTime();
        if ((start == null) != (end == null)) {
            throw new BadRequestException("Send both startTime and endTime, or neither");
        }
        if (start != null) {
            if (!start.isBefore(end)) {
                throw new BadRequestException("Start time must be before end time");
            }
            if (InstitutionPeriodSchedule.overlapsNonTeaching(start, end)) {
                throw new BadRequestException("Classes cannot be scheduled during " + nonTeachingTimes());
            }
        }
        if (request.periodNumber() == null) {
            if (start == null) {
                throw new BadRequestException("periodNumber is required (teaching periods 1-8)");
            }
            return InstitutionPeriodSchedule.teachingPeriodAt(start, end).orElseThrow(() -> new BadRequestException(
                    start + "-" + end + " is not an official teaching period (see GET /timetable/periods)"));
        }
        Period period = InstitutionPeriodSchedule.teachingPeriod(request.periodNumber()).orElseThrow(() ->
                new BadRequestException("periodNumber must be a teaching period between "
                        + InstitutionPeriodSchedule.FIRST_PERIOD + " and " + InstitutionPeriodSchedule.LAST_PERIOD));
        if (start != null && !(period.startTime().equals(start) && period.endTime().equals(end))) {
            throw new BadRequestException(period.label() + " runs " + period.startTime() + "-" + period.endTime()
                    + "; startTime/endTime must match it or be left out");
        }
        return period;
    }

    private static String nonTeachingTimes() {
        return InstitutionPeriodSchedule.DAY.stream().filter(p -> !p.isTeaching())
                .map(p -> p.label() + " (" + p.startTime() + "-" + p.endTime() + ")")
                .collect(Collectors.joining(" or "));
    }

    private void rejectConflicts(DayOfWeek day, Period period, SubjectOffering offering, Long excludeId) {
        List<TimetableSlot> conflicts = slots.findConflicts(day, period.startTime(), period.endTime(),
                offering.getSection().getId(), offering.getStaff().getId(), excludeId);
        if (conflicts.isEmpty()) {
            return;
        }
        TimetableSlot c = conflicts.get(0);
        SubjectOffering o = c.getSubjectOffering();
        String reason = o.getSection().getId().equals(offering.getSection().getId())
                ? "section " + o.getSection().getSectionName() + " already has"
                : o.getStaff().getName() + " already teaches";
        throw new ConflictException("Timetable conflict: " + reason + " " + o.getSubject().getCode() + " on "
                + c.getDayOfWeek() + " " + c.getStartTime() + "-" + c.getEndTime() + " (slot " + c.getId() + ")");
    }

    private static void apply(TimetableSlot slot, TimetableSlotRequest request, Period period, SubjectOffering offering) {
        slot.setSubjectOffering(offering);
        slot.setDayOfWeek(request.dayOfWeek());
        slot.setPeriodNumber(period.periodNumber());
        slot.setStartTime(period.startTime());
        slot.setEndTime(period.endTime());
        slot.setRoom(request.room() == null || request.room().isBlank() ? null : request.room().trim());
        slot.setSlotType(request.slotType() == null ? TimetableSlot.SlotType.LECTURE : request.slotType());
    }

    private static Map<String, Object> summary(TimetableSlot slot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slotId", slot.getId());
        m.put("subjectOfferingId", slot.getSubjectOffering().getId());
        m.put("dayOfWeek", slot.getDayOfWeek());
        m.put("periodNumber", slot.getPeriodNumber());
        m.put("startTime", slot.getStartTime().toString());
        m.put("endTime", slot.getEndTime().toString());
        m.put("active", slot.isActive());
        return m;
    }

    private TimetableSlot requireSlot(Long id) {
        return slots.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("TimetableSlot", "id", id));
    }

    private SubjectOffering requireActiveOffering(Long id) {
        SubjectOffering offering = offerings.findWithDetailsById(id)
                .orElseThrow(() -> new BadRequestException("Subject offering " + id + " does not exist"));
        if (!offering.isActive()) {
            throw new BadRequestException("This subject offering is inactive");
        }
        return offering;
    }
}
